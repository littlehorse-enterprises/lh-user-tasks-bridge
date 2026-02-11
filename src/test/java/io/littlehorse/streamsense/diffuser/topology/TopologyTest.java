package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.serde.ProtobufSerdes;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.*;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class TopologyTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLUSTER_ID = "cluster1";
    private static final String TENANT = "default";
    private static final String EXECUTION_TOPIC = "cluster1_default_execution";
    private static final String METADATA_TOPIC = "cluster1_default_metadata";
    private TopologyTestDriver testDriver;
    private TestInputTopic<String, OutputTopicRecord> executionInput;
    private TestInputTopic<String, MetadataOutputTopicRecord> metadataInput;

    @BeforeEach
    void setUp() {
        AppConfig appConfig = buildTestConfig();
        Topology topology = TopologyFactory.build(appConfig, "test", Map.of());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");
        props.put(StreamsConfig.DEFAULT_KEY_SERDE_CLASS_CONFIG, Serdes.StringSerde.class.getName());
        props.put(StreamsConfig.DEFAULT_VALUE_SERDE_CLASS_CONFIG, Serdes.ByteArraySerde.class.getName());

        testDriver = new TopologyTestDriver(topology, props);

        executionInput = testDriver.createInputTopic(
                EXECUTION_TOPIC,
                new StringSerializer(),
                ProtobufSerdes.outputTopicRecord().serializer()
        );

        metadataInput = testDriver.createInputTopic(
                METADATA_TOPIC,
                new StringSerializer(),
                ProtobufSerdes.metadataOutputTopicRecord().serializer()
        );
    }

    @AfterEach
    void tearDown() {
        if (testDriver != null) {
            testDriver.close();
        }
    }

    @Test
    void wfRunStatefulAggregation() throws Exception {
        String wfRunId = "2f5fa6471cd346258a4da55ef45b93d3";
        Timestamp ts1 = ts(1770710673, 531000000);
        Timestamp ts2 = ts(1770710733, 642000000);

        // Pipe WfSpec metadata so the filtering path is exercised
        pipeOutputTopicWfSpec("output-topic", 0, 0,
                ThreadVarDef.newBuilder()
                        .setVarDef(VariableDef.newBuilder().setName("input-name"))
                        .setAccessLevel(WfRunVariableAccessLevel.PUBLIC_VAR).build(),
                ThreadVarDef.newBuilder()
                        .setVarDef(VariableDef.newBuilder().setName("public-greeting"))
                        .setAccessLevel(WfRunVariableAccessLevel.PUBLIC_VAR).build());

        // Record 1: WfRun RUNNING
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts1)
                .setWfRun(WfRun.newBuilder()
                        .setId(WfRunId.newBuilder().setId(wfRunId))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0))
                        .setStatus(LHStatus.RUNNING)
                        .setStartTime(ts1))
                .build());

        // Record 2: Variable input-name = "Colt"
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts1)
                .setVariable(Variable.newBuilder()
                        .setId(VariableId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setName("input-name")
                                .setThreadRunNumber(0))
                        .setValue(VariableValue.newBuilder().setStr("Colt"))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0)))
                .build());

        // Record 3: Variable public-greeting = null
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts1)
                .setVariable(Variable.newBuilder()
                        .setId(VariableId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setName("public-greeting")
                                .setThreadRunNumber(0))
                        .setValue(VariableValue.newBuilder())
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0)))
                .build());

        // Record 4: TaskRun — should be skipped
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts1)
                .setTaskRun(TaskRun.newBuilder()
                        .setId(TaskRunId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setTaskGuid("0-1")))
                .build());

        // Record 5: WfRun ERROR
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts2)
                .setWfRun(WfRun.newBuilder()
                        .setId(WfRunId.newBuilder().setId(wfRunId))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0))
                        .setStatus(LHStatus.ERROR)
                        .setStartTime(ts1)
                        .setEndTime(ts2))
                .build());

        // Read all output from the WfSpec topic
        String outputTopic = "cluster1_default_streamlet_workflow-output-topic";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());

        List<KeyValue<String, byte[]>> records = output.readKeyValuesToList();
        // Should have 4 emissions: WfRun RUNNING, var input-name, var public-greeting, WfRun ERROR
        // (TaskRun was skipped)
        assertEquals(4, records.size());

        // Verify first emission: RUNNING, empty columns
        JsonNode first = MAPPER.readTree(records.get(0).value);
        assertEquals(wfRunId, first.get("id").asText());
        assertEquals("RUNNING", first.get("status").asText());
        assertTrue(first.get("columns").isEmpty());
        assertNotNull(first.get("createdAt"));

        // Verify second emission: RUNNING with input-name
        JsonNode second = MAPPER.readTree(records.get(1).value);
        assertEquals("RUNNING", second.get("status").asText());
        assertEquals("Colt", second.get("columns").get("input-name").asText());

        // Verify third emission: RUNNING with both variables
        JsonNode third = MAPPER.readTree(records.get(2).value);
        assertEquals("Colt", third.get("columns").get("input-name").asText());
        assertTrue(third.get("columns").get("public-greeting").isNull());

        // Verify last emission: ERROR with accumulated state
        JsonNode last = MAPPER.readTree(records.get(3).value);
        assertEquals("ERROR", last.get("status").asText());
        assertEquals("Colt", last.get("columns").get("input-name").asText());
    }

    @Test
    void externalEventStateless() throws Exception {
        String wfRunId = "c788297c849347609500f553eaf3b335";
        String guid = "09481e551f6f44b2b0acbbd4abbd71a1";
        Timestamp ts = ts(1770710832, 745000000);

        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setExternalEvent(ExternalEvent.newBuilder()
                        .setId(ExternalEventId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setExternalEventDefId(ExternalEventDefId.newBuilder().setName("name-event"))
                                .setGuid(guid))
                        .setContent(VariableValue.newBuilder().setStr("Obi-Wan"))
                        .setClaimed(true)
                        .setCreatedAt(ts))
                .build());

        String outputTopic = "cluster1_default_streamlet_externalevent-name-event";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());

        List<KeyValue<String, byte[]>> records = output.readKeyValuesToList();
        assertEquals(1, records.size());

        JsonNode result = MAPPER.readTree(records.get(0).value);
        assertEquals(wfRunId + "/name-event/" + guid, result.get("id").asText());
        assertEquals(wfRunId, result.get("wfRunId").asText());
        assertEquals("Obi-Wan", result.get("content").asText());
        assertTrue(result.get("claimed").asBoolean());
    }

    @Test
    void userTaskRunStateless() throws Exception {
        String wfRunId = "c59560f521b34a158d58ac7b12c50eb0";
        Timestamp ts = ts(1770711386, 748000000);

        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setUserTaskRun(UserTaskRun.newBuilder()
                        .setId(UserTaskRunId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setUserTaskGuid("0-1"))
                        .setUserTaskDefId(UserTaskDefId.newBuilder()
                                .setName("it-request")
                                .setVersion(0))
                        .setStatus(UserTaskRunStatus.ASSIGNED)
                        .setUserId("r2d2")
                        .setUserGroup("testGroup")
                        .setScheduledTime(ts))
                .build());

        String outputTopic = "cluster1_default_streamlet_usertask-it-request";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());

        List<KeyValue<String, byte[]>> records = output.readKeyValuesToList();
        assertEquals(1, records.size());

        JsonNode result = MAPPER.readTree(records.get(0).value);
        assertEquals(wfRunId + "/0-1", result.get("id").asText());
        assertEquals("it-request", result.get("userTaskDefName").asText());
        assertEquals(0, result.get("userTaskDefVersion").asInt());
        assertEquals("ASSIGNED", result.get("status").asText());
        assertEquals("r2d2", result.get("userId").asText());
        assertEquals("testGroup", result.get("userGroup").asText());
    }

    @Test
    void workflowEventStateless() throws Exception {
        String wfRunId = "26ab000bba79445dbb85f4f5ceff7a30";
        Timestamp ts = ts(1770708990, 715000000);
        Timestamp createdTs = ts(1770708991, 164000000);

        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWorkflowEvent(WorkflowEvent.newBuilder()
                        .setId(WorkflowEventId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setWorkflowEventDefId(WorkflowEventDefId.newBuilder().setName("sleep-done"))
                                .setNumber(0))
                        .setContent(VariableValue.newBuilder().setStr("hello there!"))
                        .setCreatedAt(createdTs))
                .build());

        String outputTopic = "cluster1_default_streamlet_workflowevent-sleep-done";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());

        List<KeyValue<String, byte[]>> records = output.readKeyValuesToList();
        assertEquals(1, records.size());

        JsonNode result = MAPPER.readTree(records.get(0).value);
        assertEquals(wfRunId + "/sleep-done/0", result.get("id").asText());
        assertEquals("sleep-done", result.get("workflowEventDefName").asText());
        assertEquals("hello there!", result.get("content").asText());
    }

    @Test
    void privateVariablesAreFiltered() throws Exception {
        String wfRunId = "aabb00112233445566778899aabbccdd";
        Timestamp ts = ts(1770710673, 531000000);

        // WfSpec with mixed access levels
        pipeOutputTopicWfSpec("output-topic", 0, 0,
                ThreadVarDef.newBuilder()
                        .setVarDef(VariableDef.newBuilder().setName("input-name"))
                        .setAccessLevel(WfRunVariableAccessLevel.PUBLIC_VAR).build(),
                ThreadVarDef.newBuilder()
                        .setVarDef(VariableDef.newBuilder().setName("public-greeting"))
                        .setAccessLevel(WfRunVariableAccessLevel.PUBLIC_VAR).build(),
                ThreadVarDef.newBuilder()
                        .setVarDef(VariableDef.newBuilder().setName("secret-var"))
                        .setAccessLevel(WfRunVariableAccessLevel.PRIVATE_VAR).build());

        // WfRun RUNNING
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWfRun(WfRun.newBuilder()
                        .setId(WfRunId.newBuilder().setId(wfRunId))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0))
                        .setStatus(LHStatus.RUNNING)
                        .setStartTime(ts))
                .build());

        // Variable input-name (PUBLIC)
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setVariable(Variable.newBuilder()
                        .setId(VariableId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setName("input-name")
                                .setThreadRunNumber(0))
                        .setValue(VariableValue.newBuilder().setStr("Obi-Wan"))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0)))
                .build());

        // Variable public-greeting (PUBLIC)
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setVariable(Variable.newBuilder()
                        .setId(VariableId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setName("public-greeting")
                                .setThreadRunNumber(0))
                        .setValue(VariableValue.newBuilder().setStr("Hello there!"))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0)))
                .build());

        // Variable secret-var (PRIVATE) — should be filtered out
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setVariable(Variable.newBuilder()
                        .setId(VariableId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setName("secret-var")
                                .setThreadRunNumber(0))
                        .setValue(VariableValue.newBuilder().setStr("top-secret"))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0)))
                .build());

        String outputTopic = "cluster1_default_streamlet_workflow-output-topic";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());

        List<KeyValue<String, byte[]>> records = output.readKeyValuesToList();
        // 3 emissions: WfRun RUNNING, var input-name, var public-greeting
        // (secret-var was filtered)
        assertEquals(3, records.size());

        // Verify final emission has both public vars but NOT secret-var
        JsonNode last = MAPPER.readTree(records.get(2).value);
        assertEquals("Obi-Wan", last.get("columns").get("input-name").asText());
        assertEquals("Hello there!", last.get("columns").get("public-greeting").asText());
        assertNull(last.get("columns").get("secret-var"));
    }

    @Test
    void unconfiguredSpecsAreSkipped() {
        String wfRunId = "abc123";
        Timestamp ts = ts(1770710673, 0);

        // WfRun for "unconfigured-spec" — should be skipped
        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWfRun(WfRun.newBuilder()
                        .setId(WfRunId.newBuilder().setId(wfRunId))
                        .setWfSpecId(WfSpecId.newBuilder().setName("unconfigured-spec"))
                        .setStatus(LHStatus.RUNNING)
                        .setStartTime(ts))
                .build());

        // The output topic should not exist / have no records
        String outputTopic = "cluster1_default_streamlet_workflow-unconfigured-spec";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());
        assertTrue(output.isEmpty());
    }

    @Test
    void taskRunRecordsAreSkipped() {
        String wfRunId = "abc123";
        Timestamp ts = ts(1770710673, 0);

        pipeExecution(wfRunId, OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setTaskRun(TaskRun.newBuilder()
                        .setId(TaskRunId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setTaskGuid("0-1")))
                .build());

        // No output topics should have records
        String outputTopic = "cluster1_default_streamlet_workflow-output-topic";
        TestOutputTopic<String, byte[]> output = testDriver.createOutputTopic(
                outputTopic, new StringDeserializer(), Serdes.ByteArray().deserializer());
        assertTrue(output.isEmpty());
    }

    @Test
    void multiTenantIsolation() throws Exception {
        // Configure a second tenant
        AppConfig appConfig = buildMultiTenantConfig();
        Topology topology = TopologyFactory.build(appConfig, "test-multi", Map.of());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, "test-multi");
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, "dummy:9092");

        try (TopologyTestDriver driver = new TopologyTestDriver(topology, props)) {
            TestInputTopic<String, OutputTopicRecord> tenant1Input = driver.createInputTopic(
                    "cluster1_tenant1_execution",
                    new StringSerializer(),
                    ProtobufSerdes.outputTopicRecord().serializer());

            TestInputTopic<String, OutputTopicRecord> tenant2Input = driver.createInputTopic(
                    "cluster1_tenant2_execution",
                    new StringSerializer(),
                    ProtobufSerdes.outputTopicRecord().serializer());

            Timestamp ts = ts(1770710673, 0);

            // Pipe WfRun to tenant1
            tenant1Input.pipeInput("run1", OutputTopicRecord.newBuilder()
                    .setTimestamp(ts)
                    .setWfRun(WfRun.newBuilder()
                            .setId(WfRunId.newBuilder().setId("run1"))
                            .setWfSpecId(WfSpecId.newBuilder().setName("order-processing"))
                            .setStatus(LHStatus.RUNNING)
                            .setStartTime(ts))
                    .build());

            // Pipe WfRun to tenant2
            tenant2Input.pipeInput("run2", OutputTopicRecord.newBuilder()
                    .setTimestamp(ts)
                    .setWfRun(WfRun.newBuilder()
                            .setId(WfRunId.newBuilder().setId("run2"))
                            .setWfSpecId(WfSpecId.newBuilder().setName("inventory-check"))
                            .setStatus(LHStatus.RUNNING)
                            .setStartTime(ts))
                    .build());

            // Verify isolation
            TestOutputTopic<String, byte[]> t1Output = driver.createOutputTopic(
                    "cluster1_tenant1_streamlet_workflow-order-processing",
                    new StringDeserializer(), Serdes.ByteArray().deserializer());
            TestOutputTopic<String, byte[]> t2Output = driver.createOutputTopic(
                    "cluster1_tenant2_streamlet_workflow-inventory-check",
                    new StringDeserializer(), Serdes.ByteArray().deserializer());

            assertEquals(1, t1Output.readKeyValuesToList().size());
            assertEquals(1, t2Output.readKeyValuesToList().size());

            // Cross-tenant topics should be empty
            TestOutputTopic<String, byte[]> crossCheck = driver.createOutputTopic(
                    "cluster1_tenant1_streamlet_workflow-inventory-check",
                    new StringDeserializer(), Serdes.ByteArray().deserializer());
            assertTrue(crossCheck.isEmpty());
        }
    }

    // --- Helpers ---

    private void pipeOutputTopicWfSpec(String name, int major, int revision, ThreadVarDef... varDefs) {
        String key = "WF_SPEC/%s/%05d/%05d".formatted(name, major, revision);
        MetadataOutputTopicRecord record = MetadataOutputTopicRecord.newBuilder()
                .setWfSpec(WfSpec.newBuilder()
                        .setId(WfSpecId.newBuilder().setName(name).setMajorVersion(major).setRevision(revision))
                        .putThreadSpecs("entrypoint", ThreadSpec.newBuilder()
                                .addAllVariableDefs(List.of(varDefs))
                                .build())
                        .setEntrypointThreadName("entrypoint"))
                .build();
        metadataInput.pipeInput(key, record);
    }

    private void pipeExecution(String key, OutputTopicRecord record) {
        executionInput.pipeInput(key, record);
    }

    private static Timestamp ts(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    private static AppConfig buildTestConfig() {
        return new AppConfig() {
            @Override public String lhClusterId() { return CLUSTER_ID; }
            @Override public ServerConfig server() {
                return new ServerConfig() {
                    @Override public int port() { return 8080; }
                };
            }
            @Override public List<TenantConfig> tenants() {
                return List.of(new TenantConfig() {
                    @Override public String name() { return TENANT; }
                    @Override public Optional<List<String>> wfSpecs() {
                        return Optional.of(List.of("output-topic", "await-wf-event"));
                    }
                    @Override public Optional<List<String>> userTaskDefs() {
                        return Optional.of(List.of("it-request", "approve-it-request"));
                    }
                    @Override public Optional<List<String>> externalEventDefs() {
                        return Optional.of(List.of("name-event"));
                    }
                    @Override public Optional<List<String>> workflowEventDefs() {
                        return Optional.of(List.of("sleep-done"));
                    }
                });
            }
        };
    }

    private static AppConfig buildMultiTenantConfig() {
        return new AppConfig() {
            @Override public String lhClusterId() { return CLUSTER_ID; }
            @Override public ServerConfig server() {
                return () -> 8080;
            }
            @Override public List<TenantConfig> tenants() {
                return List.of(
                        new TenantConfig() {
                            @Override public String name() { return "tenant1"; }
                            @Override public Optional<List<String>> wfSpecs() {
                                return Optional.of(List.of("order-processing"));
                            }
                            @Override public Optional<List<String>> userTaskDefs() { return Optional.empty(); }
                            @Override public Optional<List<String>> externalEventDefs() { return Optional.empty(); }
                            @Override public Optional<List<String>> workflowEventDefs() { return Optional.empty(); }
                        },
                        new TenantConfig() {
                            @Override public String name() { return "tenant2"; }
                            @Override public Optional<List<String>> wfSpecs() {
                                return Optional.of(List.of("inventory-check"));
                            }
                            @Override public Optional<List<String>> userTaskDefs() { return Optional.empty(); }
                            @Override public Optional<List<String>> externalEventDefs() { return Optional.empty(); }
                            @Override public Optional<List<String>> workflowEventDefs() { return Optional.empty(); }
                        }
                );
            }
        };
    }
}
