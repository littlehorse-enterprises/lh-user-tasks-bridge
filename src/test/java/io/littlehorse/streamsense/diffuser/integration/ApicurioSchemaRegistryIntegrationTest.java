package io.littlehorse.streamsense.diffuser.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.serde.ApicurioJsonNodeSerdeFactory;
import io.littlehorse.streamsense.diffuser.topology.TopologyFactory;
import org.apache.kafka.clients.admin.AdminClient;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.ByteArraySerializer;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
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
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.containers.wait.strategy.Wait;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration tests verifying Apicurio Schema Registry integration.
 * Spins up both Kafka and Apicurio Registry containers, processes records through the
 * full topology with Apicurio serde enabled, and verifies the Apicurio serde round-trip.
 *
 * <p>When Kafka record headers are available (as in Kafka Streams), Apicurio 3.x uses
 * <b>header-based encoding</b>: the schema globalId is written to Kafka headers while the
 * payload remains plain JSON. The deserializer reads the globalId from headers to resolve
 * the schema. Both serializer and deserializer must use the headers-aware API
 * ({@code serialize(topic, headers, data)} / {@code deserialize(topic, headers, data)}).
 *
 * <p>Schemas are pre-registered via the Apicurio v3 REST API before starting the topology.
 */
@Testcontainers
@Tag("integration")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class ApicurioSchemaRegistryIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLUSTER_ID = "cluster1";
    private static final Logger log = LoggerFactory.getLogger(ApicurioSchemaRegistryIntegrationTest.class);

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.1");

    @SuppressWarnings("resource")
    @Container
    static GenericContainer<?> apicurio = new GenericContainer<>("apicurio/apicurio-registry:3.1.7")
            .withExposedPorts(8080)
            .waitingFor(Wait.forHttp("/apis/registry/v3/system/info").forStatusCode(200));

    // --- Apicurio helpers ---

    private static String apicurioRegistryUrl() {
        return "http://%s:%d/apis/registry/v3".formatted(apicurio.getHost(), apicurio.getMappedPort(8080));
    }

    /**
     * Pre-registers a JSON Schema artifact in Apicurio Registry via the v3 REST API.
     * Uses the {@code default} group to match the TopicIdStrategy resolver.
     */
    private void registerSchema(String artifactId, String schemaJson) throws Exception {
        String body = MAPPER.writeValueAsString(Map.of(
                "artifactId", artifactId,
                "artifactType", "JSON",
                "firstVersion", Map.of(
                        "content", Map.of(
                                "content", schemaJson,
                                "contentType", "application/json"
                        )
                )
        ));
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apicurioRegistryUrl() + "/groups/default/artifacts"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        log.info("Registered schema via REST: artifactId={}, status={}", artifactId, response.statusCode());
    }

    private static final String WORKFLOW_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "status": {"type": "string"},
                "columns": {"type": "object"},
                "createdAt": {"type": "string"}
              }
            }""";

    private static final String USERTASK_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "wfRunId": {"type": "string"},
                "userTaskDefName": {"type": "string"},
                "status": {"type": "string"},
                "userId": {"type": "string"},
                "userGroup": {"type": "string"},
                "scheduledTime": {"type": "string"}
              }
            }""";

    private static final String EXTERNAL_EVENT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "wfRunId": {"type": "string"},
                "externalEventDefName": {"type": "string"},
                "content": {},
                "claimed": {"type": "boolean"},
                "createdAt": {"type": "string"}
              }
            }""";

    private static final String WORKFLOW_EVENT_SCHEMA = """
            {
              "type": "object",
              "properties": {
                "id": {"type": "string"},
                "wfRunId": {"type": "string"},
                "workflowEventDefName": {"type": "string"},
                "content": {},
                "createdAt": {"type": "string"}
              }
            }""";

    private List<JsonNode> consumeAllWithApicurioSerde(String topic, int minCount, Duration timeout) {
        List<JsonNode> results = new ArrayList<>();
        Serde<JsonNode> serde = ApicurioJsonNodeSerdeFactory.create(
                Map.of("apicurio.registry.url", apicurioRegistryUrl()));
        Deserializer<JsonNode> deserializer = serde.deserializer();

        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, kafka.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "apicurio-test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG,
                        org.apache.kafka.common.serialization.ByteArrayDeserializer.class.getName()))) {
            consumer.subscribe(List.of(topic));

            long deadline = System.currentTimeMillis() + timeout.toMillis();
            while (results.size() < minCount && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (var rec : records) {
                    results.add(deserializer.deserialize(topic, rec.headers(), rec.value()));
                }
            }
        } finally {
            deserializer.close();
            serde.close();
        }
        return results;
    }

    private JsonNode queryApicurioArtifacts() throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apicurioRegistryUrl() + "/search/artifacts?limit=100"))
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(), "Apicurio search artifacts returned non-200");
        return MAPPER.readTree(response.body());
    }

    private JsonNode getApicurioArtifactContent(String groupId, String artifactId) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create(apicurioRegistryUrl()
                        + "/groups/" + groupId + "/artifacts/" + artifactId
                        + "/versions/branch=latest/content"))
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
        assertEquals(200, response.statusCode(),
                "Failed to get artifact content for '%s' in group '%s': %s"
                        .formatted(artifactId, groupId, response.body()));
        return MAPPER.readTree(response.body());
    }

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
                }).map(AppConfig.TenantConfig.class::cast).toList();
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
        Topology topology = TopologyFactory.build(config, applicationId,
                Map.of("apicurio.registry.url", apicurioRegistryUrl()));
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

    // --- Tests ---

    @Test
    void allStreamletTypesWithApicurioSerde() throws Exception {
        String appId = "apicurio-all-types-" + UUID.randomUUID();
        String tenant = "reg";
        AppConfig config = buildConfig(
                new TenantSpec(tenant,
                        Optional.of(List.of("my-workflow")),
                        Optional.of(List.of("my-task")),
                        Optional.of(List.of("my-ext-event")),
                        Optional.of(List.of("my-wf-event")))
        );

        createTopics(config, appId);

        // Pre-register schemas in Apicurio (TopicIdStrategy convention: <topic>-value)
        registerSchema(workflowOutputTopic(tenant, "my-workflow") + "-value", WORKFLOW_SCHEMA);
        registerSchema(usertaskOutputTopic(tenant, "my-task") + "-value", USERTASK_SCHEMA);
        registerSchema(externalEventOutputTopic(tenant, "my-ext-event") + "-value", EXTERNAL_EVENT_SCHEMA);
        registerSchema(workflowEventOutputTopic(tenant, "my-wf-event") + "-value", WORKFLOW_EVENT_SCHEMA);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);
            String wfRunId = "apicurio-run-" + UUID.randomUUID().toString().replace("-", "");

            // Produce one record of each type
            produceExecution(producer, executionTopic(tenant), wfRunId,
                    wfRunRecord(wfRunId, "my-workflow", LHStatus.RUNNING, ts));
            produceExecution(producer, executionTopic(tenant), wfRunId,
                    userTaskRunRecord(wfRunId, "my-task", "ut-guid-1", ts));
            produceExecution(producer, executionTopic(tenant), wfRunId,
                    externalEventRecord(wfRunId, "my-ext-event", "ee-guid-1",
                            VariableValue.newBuilder().setStr("event-payload").build(), ts));
            produceExecution(producer, executionTopic(tenant), wfRunId,
                    workflowEventRecord(wfRunId, "my-wf-event", 0,
                            VariableValue.newBuilder().setStr("wf-event-payload").build(), ts));

            // Phase 1: Consume with Apicurio deserializer — proves serde round-trip works
            // (serializer prepends magic bytes + globalId, deserializer strips them)
            List<JsonNode> wfResults = consumeAllWithApicurioSerde(
                    workflowOutputTopic(tenant, "my-workflow"), 1, Duration.ofSeconds(30));
            assertEquals(1, wfResults.size());
            JsonNode wfNode = wfResults.get(0);
            assertNotNull(wfNode.get("id"));
            assertNotNull(wfNode.get("status"));

            List<JsonNode> utResults = consumeAllWithApicurioSerde(
                    usertaskOutputTopic(tenant, "my-task"), 1, Duration.ofSeconds(30));
            assertEquals(1, utResults.size());
            JsonNode utNode = utResults.get(0);
            assertEquals("my-task", utNode.get("userTaskDefName").asText());

            List<JsonNode> eeResults = consumeAllWithApicurioSerde(
                    externalEventOutputTopic(tenant, "my-ext-event"), 1, Duration.ofSeconds(30));
            assertEquals(1, eeResults.size());
            JsonNode eeNode = eeResults.get(0);
            assertNotNull(eeNode.get("content"));

            List<JsonNode> weResults = consumeAllWithApicurioSerde(
                    workflowEventOutputTopic(tenant, "my-wf-event"), 1, Duration.ofSeconds(30));
            assertEquals(1, weResults.size());
            JsonNode weNode = weResults.get(0);
            assertEquals("my-wf-event", weNode.get("workflowEventDefName").asText());

            // Phase 2: Verify schemas exist in Apicurio REST API
            JsonNode searchResult = queryApicurioArtifacts();
            int artifactCount = searchResult.get("count").asInt();
            assertTrue(artifactCount >= 4,
                    "Expected at least 4 artifacts in Apicurio, found " + artifactCount);

            // Build a map of artifactId -> groupId from search results
            Map<String, String> artifactGroupMap = new HashMap<>();
            for (JsonNode artifact : searchResult.get("artifacts")) {
                String gId = artifact.has("groupId") && !artifact.get("groupId").isNull()
                        ? artifact.get("groupId").asText() : "default";
                artifactGroupMap.put(artifact.get("artifactId").asText(), gId);
            }

            String[] expectedArtifactIds = {
                    workflowOutputTopic(tenant, "my-workflow") + "-value",
                    usertaskOutputTopic(tenant, "my-task") + "-value",
                    externalEventOutputTopic(tenant, "my-ext-event") + "-value",
                    workflowEventOutputTopic(tenant, "my-wf-event") + "-value"
            };
            for (String expectedId : expectedArtifactIds) {
                assertTrue(artifactGroupMap.containsKey(expectedId),
                        "Expected artifact '%s' not found in Apicurio. Found: %s"
                                .formatted(expectedId, artifactGroupMap.keySet()));
            }

            // Phase 3: Validate schema content is valid JSON Schema
            for (String aId : expectedArtifactIds) {
                String groupId = artifactGroupMap.get(aId);
                JsonNode schema = getApicurioArtifactContent(groupId, aId);
                assertTrue(schema.has("type") || schema.has("properties"),
                        "Schema for '%s' is not valid JSON Schema: %s".formatted(aId, schema));
            }
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void schemaContentReflectsJsonStructure() throws Exception {
        String appId = "apicurio-schema-check-" + UUID.randomUUID();
        String tenant = "schk";
        AppConfig config = buildConfig(
                new TenantSpec(tenant,
                        Optional.of(List.of("schema-check")),
                        Optional.empty(), Optional.empty(), Optional.empty())
        );

        createTopics(config, appId);

        // Pre-register workflow schema matching the expected output structure
        String artifactId = workflowOutputTopic(tenant, "schema-check") + "-value";
        registerSchema(artifactId, WORKFLOW_SCHEMA);

        KafkaStreams streams = startStreams(config, appId);
        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            Timestamp ts = ts(1770710673, 0);
            String wfRunId = "schema-run-" + UUID.randomUUID().toString().replace("-", "");

            produceExecution(producer, executionTopic(tenant), wfRunId,
                    wfRunRecord(wfRunId, "schema-check", LHStatus.RUNNING, ts));

            // Consume with Apicurio deserializer to verify serde round-trip
            List<JsonNode> results = consumeAllWithApicurioSerde(
                    workflowOutputTopic(tenant, "schema-check"), 1, Duration.ofSeconds(30));
            assertEquals(1, results.size());

            // Fetch and validate schema structure — find the group from the search API
            JsonNode searchResult = queryApicurioArtifacts();
            String groupId = null;
            for (JsonNode artifact : searchResult.get("artifacts")) {
                if (artifactId.equals(artifact.get("artifactId").asText())) {
                    groupId = artifact.has("groupId") && !artifact.get("groupId").isNull()
                            ? artifact.get("groupId").asText() : "default";
                    break;
                }
            }
            assertNotNull(groupId, "Artifact '%s' not found in Apicurio search results".formatted(artifactId));
            JsonNode schema = getApicurioArtifactContent(groupId, artifactId);

            assertEquals("object", schema.get("type").asText(),
                    "Schema should have type 'object'");
            assertTrue(schema.has("properties"),
                    "Schema should have 'properties' field");

            JsonNode properties = schema.get("properties");
            assertTrue(properties.has("id"), "Schema properties should contain 'id'");
            assertTrue(properties.has("status"), "Schema properties should contain 'status'");
            assertTrue(properties.has("columns"), "Schema properties should contain 'columns'");
            assertTrue(properties.has("createdAt"), "Schema properties should contain 'createdAt'");
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }
}
