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
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;

import java.time.Duration;
import java.util.*;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test using Testcontainers with a real Kafka broker.
 * Validates end-to-end flow: produce protobuf → Kafka Streams processes → consume JSON output.
 */
@Testcontainers
@Timeout(value = 60, unit = TimeUnit.SECONDS)
@org.junit.jupiter.api.Tag("integration")
class WorkflowDiffuserIntegrationTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String CLUSTER_ID = "cluster1";
    private static final String TENANT = "default";
    private static final String EXECUTION_TOPIC = "cluster1_default_execution";
    private static final String OUTPUT_TOPIC = "cluster1_default_streamlet_workflow-output-topic";
    private static final Logger log = LoggerFactory.getLogger(WorkflowDiffuserIntegrationTest.class);

    @Container
    static KafkaContainer kafka = new KafkaContainer("apache/kafka:4.1.1");

    private KafkaStreams streams;

    @BeforeEach
    void setUp() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();

        // Build and start topology
        AppConfig appConfig = buildTestConfig();
        String applicationId = "integration-test-" + UUID.randomUUID();
        Topology topology = TopologyFactory.build(appConfig, applicationId, Map.of());
        log.info("Topology built:\n{}", topology.describe());
        log.info("Starting topology with config: {}", appConfig);

        // Create input and output topics
        try (AdminClient admin = AdminClient.create(Map.of(
                "bootstrap.servers", bootstrapServers))) {
            admin.createTopics(List.of(
                    new NewTopic(EXECUTION_TOPIC, 1, (short) 1),
                    new NewTopic(OUTPUT_TOPIC, 1, (short) 1),
                    new NewTopic(applicationId + "-global-metadata-changelog", 1, (short) 1),
                    new NewTopic("cluster1_default_metadata", 1, (short) 1)
            )).all().get(10, TimeUnit.SECONDS);
        }

        Properties props = new Properties();
        props.put(StreamsConfig.APPLICATION_ID_CONFIG, applicationId);
        props.put(StreamsConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        props.put(StreamsConfig.COMMIT_INTERVAL_MS_CONFIG, 0);
        props.put(StreamsConfig.STATE_DIR_CONFIG, "/tmp/kafka-streams-test-" + UUID.randomUUID());

        streams = new KafkaStreams(topology, props);
        streams.start();

        // Wait for streams to be running
        long deadline = System.currentTimeMillis() + 30_000;
        while (streams.state() != KafkaStreams.State.RUNNING && System.currentTimeMillis() < deadline) {
            Thread.sleep(100);
        }
        assertEquals(KafkaStreams.State.RUNNING, streams.state(), "KafkaStreams did not reach RUNNING state");
    }

    @AfterEach
    void tearDown() {
        if (streams != null) {
            streams.close(Duration.ofSeconds(10));
        }
    }

    @Test
    void endToEndWfRunProcessing() throws Exception {
        String bootstrapServers = kafka.getBootstrapServers();
        String wfRunId = "e2e-test-" + UUID.randomUUID().toString().replace("-", "");
        Timestamp ts = Timestamp.newBuilder().setSeconds(1770710673).setNanos(0).build();

        // Produce a WfRun RUNNING record
        OutputTopicRecord record = OutputTopicRecord.newBuilder()
                .setTimestamp(ts)
                .setWfRun(WfRun.newBuilder()
                        .setId(WfRunId.newBuilder().setId(wfRunId))
                        .setWfSpecId(WfSpecId.newBuilder().setName("output-topic").setMajorVersion(0).setRevision(0))
                        .setStatus(LHStatus.RUNNING)
                        .setStartTime(ts))
                .build();

        try (KafkaProducer<String, byte[]> producer = new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class.getName(),
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, ByteArraySerializer.class.getName()))) {
            producer.send(new ProducerRecord<>(EXECUTION_TOPIC, wfRunId, record.toByteArray())).get();
        }

        // Consume from output topic
        try (KafkaConsumer<String, byte[]> consumer = new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers,
                ConsumerConfig.GROUP_ID_CONFIG, "test-consumer-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class.getName(),
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, ByteArrayDeserializer.class.getName()))) {
            consumer.subscribe(List.of(OUTPUT_TOPIC));

            JsonNode result = null;
            long deadline = System.currentTimeMillis() + 30_000;
            while (result == null && System.currentTimeMillis() < deadline) {
                ConsumerRecords<String, byte[]> records = consumer.poll(Duration.ofMillis(500));
                for (var rec : records) {
                    JsonNode node = MAPPER.readTree(rec.value());
                    if (wfRunId.equals(node.get("id").asText())) {
                        result = node;
                        break;
                    }
                }
            }

            assertNotNull(result, "Did not receive output record within timeout");
            assertEquals(wfRunId, result.get("id").asText());
            assertEquals("RUNNING", result.get("status").asText());
            assertNotNull(result.get("createdAt"));
            assertNotNull(result.get("columns"));
        }
    }

    private static AppConfig buildTestConfig() {
        return new AppConfig() {
            @Override public String lhClusterId() { return CLUSTER_ID; }
            @Override public ServerConfig server() { return () -> 0; }
            @Override public List<TenantConfig> tenants() {
                return List.of(new TenantConfig() {
                    @Override public String name() { return TENANT; }
                    @Override public Optional<List<String>> wfSpecs() {
                        return Optional.of(List.of("output-topic"));
                    }
                    @Override public Optional<List<String>> userTaskDefs() { return Optional.empty(); }
                    @Override public Optional<List<String>> externalEventDefs() { return Optional.empty(); }
                    @Override public Optional<List<String>> workflowEventDefs() { return Optional.empty(); }
                });
            }
            @Override public EvictionConfig eviction() {
                return new EvictionConfig() {
                    @Override public long sweepIntervalSeconds() { return 60; }
                    @Override public long fallbackRetentionSeconds() { return 2592000; }
                };
            }
        };
    }
}
