package io.littlehorse.streamsense.diffuser.integration;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
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
 * Integration test for WfRun store eviction using Testcontainers with a real Kafka broker.
 *
 * <p>Validates that terminal WfRun entries are evicted from the store after the retention
 * period, that eviction does not leak internal fields ({@code _evictionKey}) to the output
 * topic, and that variable updates for evicted WfRuns are silently skipped.
 *
 * <p>Uses a short sweep interval (2 seconds) and short fallback retention (3 seconds)
 * to make eviction observable within the test timeout.
 */
@Testcontainers
@Tag("integration")
@Timeout(value = 120, unit = TimeUnit.SECONDS)
class WfRunStoreEvictionIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLUSTER_ID = "cluster1";
    private static final String TENANT = "default";
    private static final String WF_SPEC_NAME = "eviction-test-spec";
    private static final Logger log = LoggerFactory.getLogger(WfRunStoreEvictionIntegrationTest.class);

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.1");

    // --- Topic name helpers ---

    private static String executionTopic() {
        return CLUSTER_ID + "_" + TENANT + "_execution";
    }

    private static String metadataTopic() {
        return CLUSTER_ID + "_" + TENANT + "_metadata";
    }

    private static String workflowOutputTopic() {
        return CLUSTER_ID + "_" + TENANT + "_streamlet_workflow-" + WF_SPEC_NAME;
    }

    // --- Test ---

    @Test
    void terminalWfRunIsEvictedAndVariableAfterEvictionIsSkipped() throws Exception {
        // Use a 2-second sweep interval and 3-second retention so eviction happens quickly
        AppConfig config = buildConfig(2, 3);
        String applicationId = "eviction-integration-" + UUID.randomUUID();

        createTopics(config, applicationId);
        KafkaStreams streams = startStreams(config, applicationId);

        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            String wfRunId = "evict-e2e-" + UUID.randomUUID().toString().replace("-", "");

            // Pipe WfSpec metadata with a PUBLIC_VAR so variable processing is exercised
            produceMetadata(producer, metadataTopic(),
                    "WF_SPEC/%s/00000/00000".formatted(WF_SPEC_NAME),
                    wfSpecMetadata(WF_SPEC_NAME, "test-var"));

            // Small delay to let metadata propagate to GlobalKTable
            Thread.sleep(2000);

            // Use current time so the eviction timestamp = now + 3s retention
            long nowSeconds = System.currentTimeMillis() / 1000;
            Timestamp ts = ts(nowSeconds, 0);

            // 1. Produce COMPLETED WfRun — triggers eviction scheduling
            log.info("Producing COMPLETED WfRun: {}", wfRunId);
            produceExecution(producer, executionTopic(), wfRunId,
                    wfRunRecord(wfRunId, WF_SPEC_NAME, LHStatus.COMPLETED, ts));

            // 2. Consume the output and verify _evictionKey is not present
            List<JsonNode> outputs = consumeForId(workflowOutputTopic(), wfRunId, 1, Duration.ofSeconds(30));
            assertEquals(1, outputs.size(), "Expected 1 output record for COMPLETED WfRun");
            JsonNode output = outputs.getFirst();
            assertEquals("COMPLETED", output.get("status").asText());
            assertFalse(output.has("_evictionKey"), "_evictionKey must not leak to output topic");
            log.info("Verified COMPLETED output — no _evictionKey");

            // 3. Wait for eviction: retention (3s) + sweep interval (2s) + processing margin
            log.info("Waiting for eviction (retention=3s, sweep=2s)...");
            Thread.sleep(8000);

            // 4. Produce a Variable for the (now evicted) WfRun — should be silently skipped
            log.info("Producing Variable for evicted WfRun: {}", wfRunId);
            Timestamp lateTs = ts(System.currentTimeMillis() / 1000, 0);
            produceExecution(producer, executionTopic(), wfRunId,
                    variableRecord(wfRunId, WF_SPEC_NAME, "test-var",
                            VariableValue.newBuilder().setStr("late-arrival").build(), lateTs));

            // 5. Wait briefly and verify no new output was emitted for this WfRun
            Thread.sleep(3000);
            List<JsonNode> allForId = consumeForId(workflowOutputTopic(), wfRunId, 2, Duration.ofSeconds(5));
            assertEquals(1, allForId.size(),
                    "Variable for evicted WfRun should not produce additional output");
            log.info("Verified: no output for variable after eviction");
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void nonTerminalWfRunIsNotEvicted() throws Exception {
        AppConfig config = buildConfig(2, 3);
        String applicationId = "eviction-nonterminal-" + UUID.randomUUID();

        createTopics(config, applicationId);
        KafkaStreams streams = startStreams(config, applicationId);

        try (KafkaProducer<String, byte[]> producer = createProducer()) {
            String wfRunId = "running-e2e-" + UUID.randomUUID().toString().replace("-", "");

            long nowSeconds = System.currentTimeMillis() / 1000;
            Timestamp ts = ts(nowSeconds, 0);

            // Produce RUNNING WfRun — no eviction scheduled
            log.info("Producing RUNNING WfRun: {}", wfRunId);
            produceExecution(producer, executionTopic(), wfRunId,
                    wfRunRecord(wfRunId, WF_SPEC_NAME, LHStatus.RUNNING, ts));

            // Consume the initial output
            List<JsonNode> outputs = consumeForId(workflowOutputTopic(), wfRunId, 1, Duration.ofSeconds(30));
            assertEquals(1, outputs.size());
            assertEquals("RUNNING", outputs.getFirst().get("status").asText());

            // Wait well beyond the retention + sweep interval
            log.info("Waiting to confirm RUNNING WfRun is NOT evicted...");
            Thread.sleep(8000);

            // Pipe WfSpec metadata so variable processing works
            produceMetadata(producer, metadataTopic(),
                    "WF_SPEC/%s/00000/00000".formatted(WF_SPEC_NAME),
                    wfSpecMetadata(WF_SPEC_NAME, "test-var"));
            Thread.sleep(2000);

            // Produce a Variable — should succeed because the WfRun is still in the store
            Timestamp lateTs = ts(System.currentTimeMillis() / 1000, 0);
            produceExecution(producer, executionTopic(), wfRunId,
                    variableRecord(wfRunId, WF_SPEC_NAME, "test-var",
                            VariableValue.newBuilder().setStr("still-alive").build(), lateTs));

            // Should get 2 records for this WfRun: RUNNING + variable update
            List<JsonNode> allOutputs = consumeForId(workflowOutputTopic(), wfRunId, 2, Duration.ofSeconds(30));
            assertEquals(2, allOutputs.size(), "RUNNING WfRun should NOT be evicted — variable update should succeed");
            assertEquals("still-alive", allOutputs.get(1).get("columns").get("test-var").asText());
            log.info("Verified: RUNNING WfRun not evicted, variable update succeeded");
        } finally {
            streams.close(Duration.ofSeconds(10));
        }
    }

    // --- Config ---

    private static AppConfig buildConfig(long sweepIntervalSeconds, long fallbackRetentionSeconds) {
        return new AppConfig() {
            @Override public String lhClusterId() { return CLUSTER_ID; }
            @Override public ServerConfig server() { return () -> 0; }
            @Override public List<TenantConfig> tenants() {
                return List.of(new TenantConfig() {
                    @Override public String name() { return TENANT; }
                    @Override public Optional<List<String>> wfSpecs() {
                        return Optional.of(List.of(WF_SPEC_NAME));
                    }
                    @Override public Optional<List<String>> userTaskDefs() { return Optional.empty(); }
                    @Override public Optional<List<String>> externalEventDefs() { return Optional.empty(); }
                    @Override public Optional<List<String>> workflowEventDefs() { return Optional.empty(); }
                });
            }
            @Override public EvictionConfig eviction() {
                return new EvictionConfig() {
                    @Override public long sweepIntervalSeconds() { return sweepIntervalSeconds; }
                    @Override public long fallbackRetentionSeconds() { return fallbackRetentionSeconds; }
                };
            }
        };
    }

    // --- Infrastructure helpers ---

    private void createTopics(AppConfig config, String applicationId) throws Exception {
        List<NewTopic> topics = new ArrayList<>();
        topics.add(new NewTopic(executionTopic(), 1, (short) 1));
        topics.add(new NewTopic(metadataTopic(), 1, (short) 1));
        topics.add(new NewTopic(workflowOutputTopic(), 1, (short) 1));
        topics.add(new NewTopic(applicationId + "-global-metadata-changelog", 1, (short) 1));

        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", kafka.getBootstrapServers()))) {
            // Ignore TopicExistsException — shared topics may already exist from a prior test
            for (NewTopic topic : topics) {
                try {
                    admin.createTopics(List.of(topic)).all().get(10, TimeUnit.SECONDS);
                } catch (java.util.concurrent.ExecutionException e) {
                    if (!(e.getCause() instanceof org.apache.kafka.common.errors.TopicExistsException)) {
                        throw e;
                    }
                }
            }
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

    private List<JsonNode> consumeForId(String topic, String wfRunId, int expectedMinCount,
                                        Duration timeout) throws Exception {
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
                    JsonNode node = MAPPER.readTree(rec.value());
                    if (wfRunId.equals(node.get("id").asText())) {
                        results.add(node);
                    }
                }
            }
        }
        return results;
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

    // --- Protobuf builders ---

    private static Timestamp ts(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }

    private static OutputTopicRecord wfRunRecord(String wfRunId, String wfSpecName, LHStatus status, Timestamp ts) {
        WfRun.Builder builder = WfRun.newBuilder()
                .setId(WfRunId.newBuilder().setId(wfRunId))
                .setWfSpecId(WfSpecId.newBuilder().setName(wfSpecName).setMajorVersion(0).setRevision(0))
                .setStatus(status)
                .setStartTime(ts);
        if (status == LHStatus.COMPLETED || status == LHStatus.ERROR || status == LHStatus.EXCEPTION) {
            builder.setEndTime(ts);
        }
        return OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWfRun(builder)
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

    private static MetadataOutputTopicRecord wfSpecMetadata(String name, String... publicVarNames) {
        ThreadSpec.Builder threadSpec = ThreadSpec.newBuilder();
        for (String varName : publicVarNames) {
            threadSpec.addVariableDefs(ThreadVarDef.newBuilder()
                    .setVarDef(VariableDef.newBuilder().setName(varName))
                    .setAccessLevel(WfRunVariableAccessLevel.PUBLIC_VAR));
        }
        return MetadataOutputTopicRecord.newBuilder()
                .setWfSpec(WfSpec.newBuilder()
                        .setId(WfSpecId.newBuilder().setName(name).setMajorVersion(0).setRevision(0))
                        .putThreadSpecs("entrypoint", threadSpec.build())
                        .setEntrypointThreadName("entrypoint"))
                .build();
    }
}
