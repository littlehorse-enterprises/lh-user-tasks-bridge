package io.littlehorse.streamsense.diffuser.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.topology.TopologyFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArrayDeserializer;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.StringDeserializer;
import org.apache.kafka.common.serialization.StringSerializer;
import org.apache.kafka.streams.KafkaStreams;
import org.apache.kafka.streams.StreamsConfig;
import org.apache.kafka.streams.Topology;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.Map;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Multi-scenario integration tests using Testcontainers with a real Kafka broker.
 * Covers multi-tenant routing, all streamlet types, config-based filtering,
 * variable access levels, and multi-spec topic routing.
 */
@Testcontainers
@Tag("integration")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class MultiScenarioIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLUSTER_ID = "cluster1";
    private static final Logger log = LoggerFactory.getLogger(MultiScenarioIntegrationTest.class);

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.1");

    // --- Topic name helpers ---

    private static String executionTopic(String tenant) {
        return CLUSTER_ID + "_" + tenant + "_execution";
    }

    private static String metadataTopic(String tenant) {
        return CLUSTER_ID + "_" + tenant + "_metadata";
    }

    private static String workflowOutputTopic(String tenant, String spec) {
        return CLUSTER_ID + "_" + tenant + "_streamlet_workflow-" + spec;
    }

    private static String usertaskOutputTopic(String tenant, String spec) {
        return CLUSTER_ID + "_" + tenant + "_streamlet_usertask-" + spec;
    }

    private static String externalEventOutputTopic(String tenant, String spec) {
        return CLUSTER_ID + "_" + tenant + "_streamlet_externalevent-" + spec;
    }

    private static String workflowEventOutputTopic(String tenant, String spec) {
        return CLUSTER_ID + "_" + tenant + "_streamlet_workflowevent-" + spec;
    }

    // --- Config builder ---

    record TenantSpec(
            String name,
            Optional<List<String>> wfSpecs,
            Optional<List<String>> userTaskDefs,
            Optional<List<String>> externalEventDefs,
            Optional<List<String>> workflowEventDefs
    ) {}

    private static AppConfig buildConfig(TenantSpec... tenantSpecs) {
        return new AppConfig() {
            @Override public String lhClusterId() { return CLUSTER_ID; }
            @Override public ServerConfig server() { return () -> 0; }
            @Override public List<TenantConfig> tenants() {
                return Arrays.stream(tenantSpecs).map(ts -> new TenantConfig() {
                    @Override public String name() { return ts.name(); }
                    @Override public Optional<List<String>> wfSpecs() { return ts.wfSpecs(); }
                    @Override public Optional<List<String>> userTaskDefs() { return ts.userTaskDefs(); }
                    @Override public Optional<List<String>> externalEventDefs() { return ts.externalEventDefs(); }
                    @Override public Optional<List<String>> workflowEventDefs() { return ts.workflowEventDefs(); }
                }).map(TenantConfig.class::cast).toList();
            }
            @Override public EvictionConfig eviction() {
                return new EvictionConfig() {
                    @Override public long sweepIntervalSeconds() { return 60; }
                    @Override public long fallbackRetentionSeconds() { return 2592000; }
                };
            }
        };
    }

    // --- Infrastructure helpers ---

    private void createTopics(AppConfig config, String applicationId, String... additionalTopics) throws Exception {
        List<NewTopic> topics = new ArrayList<>();
        for (AppConfig.TenantConfig tenant : config.tenants()) {
            topics.add(new NewTopic(executionTopic(tenant.name()), 1, (short) 1));
            topics.add(new NewTopic(metadataTopic(tenant.name()), 1, (short) 1));

            tenant.wfSpecs().ifPresent(specs -> specs.forEach(spec ->
                    topics.add(new NewTopic(workflowOutputTopic(tenant.name(), spec), 1, (short) 1))));
            tenant.userTaskDefs().ifPresent(specs -> specs.forEach(spec ->
                    topics.add(new NewTopic(usertaskOutputTopic(tenant.name(), spec), 1, (short) 1))));
            tenant.externalEventDefs().ifPresent(specs -> specs.forEach(spec ->
                    topics.add(new NewTopic(externalEventOutputTopic(tenant.name(), spec), 1, (short) 1))));
            tenant.workflowEventDefs().ifPresent(specs -> specs.forEach(spec ->
                    topics.add(new NewTopic(workflowEventOutputTopic(tenant.name(), spec), 1, (short) 1))));
        }

        topics.add(new NewTopic(applicationId + "-global-metadata-changelog", 1, (short) 1));
        for (String extra : additionalTopics) {
            topics.add(new NewTopic(extra, 1, (short) 1));
        }

        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafka.getBootstrapServers()))) {
            admin.createTopics(topics).all().get(10, TimeUnit.SECONDS);
        }
    }

    private KafkaStreams startStreams(AppConfig config, String applicationId) throws Exception {
        Topology topology = TopologyFactory.build(config, applicationId, Map.of());
        log.info("Topology built:\n{}", topology.describe());

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers());
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 0);
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams-test-" + UUID.randomUUID());

        KafkaStreams streams = new KafkaStreams(topology, props);
        streams.start();

        long deadline = System.currentTimeMillis() + 30_000;
        while (streams.state() != KafkaStreams.State.RUNNING && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(KafkaStreams.State.RUNNING, streams.state(), "KafkaStreams did not reach RUNNING state");
        return streams;
    }

    private KafkaProducer<String, byte[]> createProducer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName()));
    }

    private void produceExecution(KafkaProducer<String, byte[]> producer, String topic, String key,
                                  OutputTopicRecord record) throws Exception {
        producer.send(new ProducerRecord<>(topic, key, record.toByteArray())).get();
    }

    private void produceMetadata(KafkaProducer<String, byte[]> producer, String topic, String key,
                                 MetadataOutputTopicRecord record) throws Exception {
        producer.send(new ProducerRecord<>(topic, key, record.toByteArray())).get();
    }

    private List<JsonNode> consumeAll(String topic, int expectedMinCount, Duration timeout) throws Exception {
        List<JsonNode> results = new ArrayList<>();
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()))) {
            consumer.subscribe(List.of(topic));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (results.size() < expectedMinCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (var rec : records) {
                    results.add(MAPPER.readTree(rec.value()));
                }
            }
        }
        return results;
    }

    private void assertTopicEmpty(String topic) throws Exception {
        List<JsonNode> records = consumeAll(topic, 1, Duration.ofSeconds(5));
        assertEquals(0, records.size(), "Expected topic '%s' to be empty but found %d records".formatted(topic, records.size()));
    }

    // --- Protobuf builders ---

    private static Timestamp ts(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    private static OutputTopicRecord wfRunRecord(String wfRunId, String wfSpecName, LHStatus status, Timestamp ts) {
        return OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWfRun(WfRun.newBuilder()
                        .setId(WfRunId.newBuilder().setId(wfRunId))
                        .setWfSpecId(WfSpecId.newBuilder().setName(wfSpecName).setMajorVersion(0).setRevision(0))
                        .setStatus(status)
                        .setStartTime(ts))
                .build();
    }

    private static OutputTopicRecord variableRecord(String wfRunId, String wfSpecName, String varName,
                                                    VariableValue value, Timestamp ts) {
        return OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setVariable(Variable.newBuilder()
                        .setId(VariableId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setName(varName)
                                .setThreadRunNumber(0))
                        .setValue(value)
                        .setWfSpecId(WfSpecId.newBuilder().setName(wfSpecName).setMajorVersion(0).setRevision(0)))
                .build();
    }

    private static OutputTopicRecord userTaskRunRecord(String wfRunId, String defName, String guid, Timestamp ts) {
        return OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setUserTaskRun(UserTaskRun.newBuilder()
                        .setId(UserTaskRunId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setUserTaskGuid(guid))
                        .setUserTaskDefId(UserTaskDefId.newBuilder().setName(defName).setVersion(0))
                        .setStatus(UserTaskRunStatus.ASSIGNED)
                        .setUserId("test-user")
                        .setUserGroup("test-group")
                        .setScheduledTime(ts))
                .build();
    }

    private static OutputTopicRecord externalEventRecord(String wfRunId, String defName, String guid,
                                                         VariableValue content, Timestamp ts) {
        return OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setExternalEvent(ExternalEvent.newBuilder()
                        .setId(ExternalEventId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setExternalEventDefId(ExternalEventDefId.newBuilder().setName(defName))
                                .setGuid(guid))
                        .setContent(content)
                        .setClaimed(true)
                        .setCreatedAt(ts))
                .build();
    }

    private static OutputTopicRecord workflowEventRecord(String wfRunId, String defName, int number,
                                                         VariableValue content, Timestamp ts) {
        return OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWorkflowEvent(WorkflowEvent.newBuilder()
                        .setId(WorkflowEventId.newBuilder()
                                .setWfRunId(WfRunId.newBuilder().setId(wfRunId))
                                .setWorkflowEventDefId(WorkflowEventDefId.newBuilder().setName(defName))
                                .setNumber(number))
                        .setContent(content)
                        .setCreatedAt(ts))
                .build();
    }

    private static Map.Entry<String, MetadataOutputTopicRecord> wfSpecMetadata(
            String specName, int major, int revision, ThreadVarDef... varDefs) {
        String key = "WF_SPEC/%s/%05d/%05d".formatted(specName, major, revision);
        MetadataOutputTopicRecord record = MetadataOutputTopicRecord.newBuilder()
                .setWfSpec(WfSpec.newBuilder()
                        .setId(WfSpecId.newBuilder().setName(specName).setMajorVersion(major).setRevision(revision))
                        .putThreadSpecs("entrypoint", ThreadSpec.newBuilder()
                                .addAllVariableDefs(List.of(varDefs))
                                .build())
                        .setEntrypointThreadName("entrypoint"))
                .build();
        return Map.entry(key, record);
    }

    private static ThreadVarDef threadVarDef(String name, WfRunVariableAccessLevel accessLevel) {
        return ThreadVarDef.newBuilder()
                .setVarDef(VariableDef.newBuilder().setName(name))
                .setAccessLevel(accessLevel)
                .build();
    }

    // --- Tests ---

    @Test
    void multiTenantPartiallyEnabled() throws Exception {
        String appId = "multi-tenant-" + UUID.randomUUID();
        AppConfig config = buildConfig(
                new TenantSpec("alpha", Optional.of(List.of("order-flow")),
                        Optional.empty(), Optional.empty(), Optional.empty()),
                new TenantSpec("beta", Optional.of(List.of("inventory-check")),
                        Optional.empty(), Optional.empty(), Optional.empty()),
                new TenantSpec("gamma", Optional.empty(),
                        Optional.empty(), Optional.empty(), Optional.empty())
        );

        // Pre-create gamma output topic for empty assertion
        String gammaOutputTopic = workflowOutputTopic("gamma", "order-flow");
        createTopics(config, appId, gammaOutputTopic);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);

            produceExecution(producer, executionTopic("alpha"), "run-alpha",
                    wfRunRecord("run-alpha", "order-flow", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic("beta"), "run-beta",
                    wfRunRecord("run-beta", "inventory-check", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic("gamma"), "run-gamma",
                    wfRunRecord("run-gamma", "order-flow", LHStatus.RUNNING, ts));

            // Alpha: 1 record
            List<JsonNode> alphaResults = consumeAll(
                    workflowOutputTopic("alpha", "order-flow"), 1, Duration.ofSeconds(30));
            assertEquals(1, alphaResults.size());
            assertEquals("RUNNING", alphaResults.get(0).get("status").asText());

            // Beta: 1 record
            List<JsonNode> betaResults = consumeAll(
                    workflowOutputTopic("beta", "inventory-check"), 1, Duration.ofSeconds(30));
            assertEquals(1, betaResults.size());
            assertEquals("RUNNING", betaResults.get(0).get("status").asText());

            // Gamma: 0 records (unconfigured tenant — no wfSpecs)
            assertTopicEmpty(gammaOutputTopic);
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void allStreamletTypesForSingleTenant() throws Exception {
        String appId = "all-types-" + UUID.randomUUID();
        AppConfig config = buildConfig(
                new TenantSpec("full",
                        Optional.of(List.of("my-workflow")),
                        Optional.of(List.of("my-task")),
                        Optional.of(List.of("my-ext-event")),
                        Optional.of(List.of("my-wf-event")))
        );

        createTopics(config, appId);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);
            String wfRunId = "fullrun-" + UUID.randomUUID().toString().replace("-", "");

            // WfRun
            produceExecution(producer, executionTopic("full"), wfRunId,
                    wfRunRecord(wfRunId, "my-workflow", LHStatus.RUNNING, ts));
            // UserTaskRun
            produceExecution(producer, executionTopic("full"), wfRunId,
                    userTaskRunRecord(wfRunId, "my-task", "ut-guid-1", ts));
            // ExternalEvent
            produceExecution(producer, executionTopic("full"), wfRunId,
                    externalEventRecord(wfRunId, "my-ext-event", "ee-guid-1",
                            VariableValue.newBuilder().setStr("event-payload").build(), ts));
            // WorkflowEvent
            produceExecution(producer, executionTopic("full"), wfRunId,
                    workflowEventRecord(wfRunId, "my-wf-event", 0,
                            VariableValue.newBuilder().setStr("wf-event-payload").build(), ts));

            // Workflow output
            List<JsonNode> wfResults = consumeAll(
                    workflowOutputTopic("full", "my-workflow"), 1, Duration.ofSeconds(30));
            assertEquals(1, wfResults.size());
            JsonNode wfNode = wfResults.get(0);
            assertNotNull(wfNode.get("id"));
            assertNotNull(wfNode.get("status"));
            assertNotNull(wfNode.get("columns"));
            assertNotNull(wfNode.get("createdAt"));

            // UserTask output
            List<JsonNode> utResults = consumeAll(
                    usertaskOutputTopic("full", "my-task"), 1, Duration.ofSeconds(30));
            assertEquals(1, utResults.size());
            JsonNode utNode = utResults.get(0);
            assertNotNull(utNode.get("id"));
            assertNotNull(utNode.get("wfRunId"));
            assertEquals("my-task", utNode.get("userTaskDefName").asText());
            assertNotNull(utNode.get("status"));
            assertNotNull(utNode.get("userId"));
            assertNotNull(utNode.get("userGroup"));

            // ExternalEvent output
            List<JsonNode> eeResults = consumeAll(
                    externalEventOutputTopic("full", "my-ext-event"), 1, Duration.ofSeconds(30));
            assertEquals(1, eeResults.size());
            JsonNode eeNode = eeResults.get(0);
            assertNotNull(eeNode.get("id"));
            assertNotNull(eeNode.get("wfRunId"));
            assertNotNull(eeNode.get("content"));
            assertNotNull(eeNode.get("claimed"));
            assertNotNull(eeNode.get("createdAt"));

            // WorkflowEvent output
            List<JsonNode> weResults = consumeAll(
                    workflowEventOutputTopic("full", "my-wf-event"), 1, Duration.ofSeconds(30));
            assertEquals(1, weResults.size());
            JsonNode weNode = weResults.get(0);
            assertNotNull(weNode.get("id"));
            assertNotNull(weNode.get("wfRunId"));
            assertEquals("my-wf-event", weNode.get("workflowEventDefName").asText());
            assertNotNull(weNode.get("content"));
            assertNotNull(weNode.get("createdAt"));
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void wfSpecFilteringByConfig() throws Exception {
        String appId = "filtering-" + UUID.randomUUID();
        AppConfig config = buildConfig(
                new TenantSpec("filtered", Optional.of(List.of("enabled-spec")),
                        Optional.empty(), Optional.empty(), Optional.empty())
        );

        String disabledOutputTopic = workflowOutputTopic("filtered", "disabled-spec");
        createTopics(config, appId, disabledOutputTopic);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);

            produceExecution(producer, executionTopic("filtered"), "run-enabled",
                    wfRunRecord("run-enabled", "enabled-spec", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic("filtered"), "run-disabled",
                    wfRunRecord("run-disabled", "disabled-spec", LHStatus.RUNNING, ts));

            // Enabled spec: 1 record
            List<JsonNode> enabledResults = consumeAll(
                    workflowOutputTopic("filtered", "enabled-spec"), 1, Duration.ofSeconds(30));
            assertEquals(1, enabledResults.size());

            // Disabled spec: 0 records
            assertTopicEmpty(disabledOutputTopic);
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void allVariableTypesPublicAndPrivate() throws Exception {
        String appId = "vartest-" + UUID.randomUUID();
        AppConfig config = buildConfig(
                new TenantSpec("vartest", Optional.of(List.of("var-spec")),
                        Optional.empty(), Optional.empty(), Optional.empty())
        );

        createTopics(config, appId);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);
            String wfRunId = "varrun-" + UUID.randomUUID().toString().replace("-", "");

            // Build WfSpec metadata with 18 ThreadVarDefs: 9 PUBLIC + 9 PRIVATE
            Map.Entry<String, MetadataOutputTopicRecord> meta = wfSpecMetadata("var-spec", 0, 0,
                    threadVarDef("pub-str", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-str", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-int", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-int", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-double", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-double", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-bool", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-bool", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-json-obj", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-json-obj", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-json-arr", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-json-arr", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-bytes", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-bytes", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-wf-run-id", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-wf-run-id", WfRunVariableAccessLevel.PRIVATE_VAR),
                    threadVarDef("pub-utc-timestamp", WfRunVariableAccessLevel.PUBLIC_VAR),
                    threadVarDef("priv-utc-timestamp", WfRunVariableAccessLevel.PRIVATE_VAR)
            );

            // Pipe WfSpec metadata and wait for GlobalKTable propagation
            produceMetadata(producer, metadataTopic("vartest"), meta.getKey(), meta.getValue());
            Thread.sleep(5_000);

            // WfRun RUNNING
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    wfRunRecord(wfRunId, "var-spec", LHStatus.RUNNING, ts));

            // 9 PUBLIC variables
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-str",
                            VariableValue.newBuilder().setStr("hello").build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-int",
                            VariableValue.newBuilder().setInt(42).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-double",
                            VariableValue.newBuilder().setDouble(3.14).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-bool",
                            VariableValue.newBuilder().setBool(true).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-json-obj",
                            VariableValue.newBuilder().setJsonObj("{\"key\":\"value\"}").build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-json-arr",
                            VariableValue.newBuilder().setJsonArr("[1,2,3]").build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-bytes",
                            VariableValue.newBuilder().setBytes(ByteString.copyFromUtf8("binary-data")).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-wf-run-id",
                            VariableValue.newBuilder().setWfRunId(WfRunId.newBuilder().setId("ref-run-123")).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "pub-utc-timestamp",
                            VariableValue.newBuilder().setUtcTimestamp(ts(1770710999, 0)).build(), ts));

            // 9 PRIVATE variables
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-str",
                            VariableValue.newBuilder().setStr("secret").build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-int",
                            VariableValue.newBuilder().setInt(999).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-double",
                            VariableValue.newBuilder().setDouble(1.23).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-bool",
                            VariableValue.newBuilder().setBool(false).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-json-obj",
                            VariableValue.newBuilder().setJsonObj("{\"secret\":true}").build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-json-arr",
                            VariableValue.newBuilder().setJsonArr("[4,5,6]").build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-bytes",
                            VariableValue.newBuilder().setBytes(ByteString.copyFromUtf8("secret-data")).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-wf-run-id",
                            VariableValue.newBuilder().setWfRunId(WfRunId.newBuilder().setId("secret-ref")).build(), ts));
            produceExecution(producer, executionTopic("vartest"), wfRunId,
                    variableRecord(wfRunId, "var-spec", "priv-utc-timestamp",
                            VariableValue.newBuilder().setUtcTimestamp(ts(1770711000, 0)).build(), ts));

            // Expect 10 output records: 1 WfRun + 9 public variables (9 private filtered)
            String outputTopic = workflowOutputTopic("vartest", "var-spec");
            List<JsonNode> results = consumeAll(outputTopic, 10, Duration.ofSeconds(30));
            assertEquals(10, results.size(),
                    "Expected 10 records (1 WfRun + 9 public vars), got " + results.size()
                            + ". If >10, private variables leaked (GlobalKTable may not have loaded in time).");

            // Last record should have the fully accumulated state
            JsonNode last = results.get(results.size() - 1);
            JsonNode columns = last.get("columns");

            // Exactly 9 public variable keys
            assertEquals(9, columns.size(), "Expected 9 public variables in columns");

            // Verify each public variable type
            assertTrue(columns.get("pub-str").isTextual());
            assertEquals("hello", columns.get("pub-str").asText());

            assertTrue(columns.get("pub-int").isNumber());
            assertEquals(42, columns.get("pub-int").asLong());

            assertTrue(columns.get("pub-double").isNumber());
            assertEquals(3.14, columns.get("pub-double").asDouble(), 0.001);

            assertTrue(columns.get("pub-bool").isBoolean());
            assertTrue(columns.get("pub-bool").asBoolean());

            assertTrue(columns.get("pub-json-obj").isObject());
            assertEquals("value", columns.get("pub-json-obj").get("key").asText());

            assertTrue(columns.get("pub-json-arr").isArray());
            assertEquals(3, columns.get("pub-json-arr").size());

            assertTrue(columns.get("pub-bytes").isTextual()); // base64 encoded

            assertTrue(columns.get("pub-wf-run-id").isTextual());
            assertEquals("ref-run-123", columns.get("pub-wf-run-id").asText());

            assertTrue(columns.get("pub-utc-timestamp").isTextual()); // ISO-8601

            // No private variables should be present
            assertNull(columns.get("priv-str"));
            assertNull(columns.get("priv-int"));
            assertNull(columns.get("priv-double"));
            assertNull(columns.get("priv-bool"));
            assertNull(columns.get("priv-json-obj"));
            assertNull(columns.get("priv-json-arr"));
            assertNull(columns.get("priv-bytes"));
            assertNull(columns.get("priv-wf-run-id"));
            assertNull(columns.get("priv-utc-timestamp"));
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void multipleWfSpecsRoutedToDifferentTopics() throws Exception {
        String appId = "routing-" + UUID.randomUUID();
        AppConfig config = buildConfig(
                new TenantSpec("routing", Optional.of(List.of("orders", "payments", "shipping")),
                        Optional.empty(), Optional.empty(), Optional.empty())
        );

        createTopics(config, appId);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);
            String ordersRunId = "orders-" + UUID.randomUUID().toString().replace("-", "");
            String paymentsRunId = "payments-" + UUID.randomUUID().toString().replace("-", "");
            String shippingRunId = "shipping-" + UUID.randomUUID().toString().replace("-", "");

            // Orders: WfRun + Variable "order-id" = STR
            produceExecution(producer, executionTopic("routing"), ordersRunId,
                    wfRunRecord(ordersRunId, "orders", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic("routing"), ordersRunId,
                    variableRecord(ordersRunId, "orders", "order-id",
                            VariableValue.newBuilder().setStr("ORD-001").build(), ts));

            // Payments: WfRun + Variable "amount" = DOUBLE
            produceExecution(producer, executionTopic("routing"), paymentsRunId,
                    wfRunRecord(paymentsRunId, "payments", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic("routing"), paymentsRunId,
                    variableRecord(paymentsRunId, "payments", "amount",
                            VariableValue.newBuilder().setDouble(99.99).build(), ts));

            // Shipping: WfRun + Variable "tracking" = STR
            produceExecution(producer, executionTopic("routing"), shippingRunId,
                    wfRunRecord(shippingRunId, "shipping", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic("routing"), shippingRunId,
                    variableRecord(shippingRunId, "shipping", "tracking",
                            VariableValue.newBuilder().setStr("TRACK-XYZ").build(), ts));

            // Orders topic: 2 records, last has columns.order-id
            List<JsonNode> ordersResults = consumeAll(
                    workflowOutputTopic("routing", "orders"), 2, Duration.ofSeconds(30));
            assertEquals(2, ordersResults.size());
            JsonNode ordersLast = ordersResults.get(ordersResults.size() - 1);
            assertEquals("ORD-001", ordersLast.get("columns").get("order-id").asText());
            assertNull(ordersLast.get("columns").get("amount"));
            assertNull(ordersLast.get("columns").get("tracking"));

            // Payments topic: 2 records, last has columns.amount
            List<JsonNode> paymentsResults = consumeAll(
                    workflowOutputTopic("routing", "payments"), 2, Duration.ofSeconds(30));
            assertEquals(2, paymentsResults.size());
            JsonNode paymentsLast = paymentsResults.get(paymentsResults.size() - 1);
            assertEquals(99.99, paymentsLast.get("columns").get("amount").asDouble(), 0.001);
            assertNull(paymentsLast.get("columns").get("order-id"));
            assertNull(paymentsLast.get("columns").get("tracking"));

            // Shipping topic: 2 records, last has columns.tracking
            List<JsonNode> shippingResults = consumeAll(
                    workflowOutputTopic("routing", "shipping"), 2, Duration.ofSeconds(30));
            assertEquals(2, shippingResults.size());
            JsonNode shippingLast = shippingResults.get(shippingResults.size() - 1);
            assertEquals("TRACK-XYZ", shippingLast.get("columns").get("tracking").asText());
            assertNull(shippingLast.get("columns").get("order-id"));
            assertNull(shippingLast.get("columns").get("amount"));
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }
}
