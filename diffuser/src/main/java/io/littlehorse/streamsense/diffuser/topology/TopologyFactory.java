package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import io.littlehorse.sdk.common.proto.MetadataOutputTopicRecord;
import io.littlehorse.sdk.common.proto.OutputTopicRecord;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.serde.ApicurioJsonNodeSerdeFactory;
import io.littlehorse.streamsense.diffuser.serde.ProtobufSerdes;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.utils.Bytes;
import org.apache.kafka.streams.AutoOffsetReset;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Builds the complete Kafka Streams topology using the {@link StreamsBuilder} DSL.
 *
 * <p>The topology has two data paths:
 * <ol>
 *   <li><b>Metadata path:</b> metadata-source → MetadataProcessor (stores raw bytes,
 *       keyed by tenant) → automatic changelog → GlobalKTable (deserialized as protobuf,
 *       available to all execution-path tasks)</li>
 *   <li><b>Execution path:</b> execution-source → DiffuserProcessor (routes, transforms,
 *       enriches with metadata from the GlobalKTable) → streamlet-sink (dynamic topic
 *       per streamlet, resolved from the {@code streamlet-topic} header)</li>
 * </ol>
 */
public class TopologyFactory {

    private static final Logger log = LoggerFactory.getLogger(TopologyFactory.class);

    /**
     * Builds and returns the Kafka Streams {@link Topology}.
     *
     * @param appConfig      application configuration (cluster ID, tenants, specs)
     * @param applicationId  Kafka Streams {@code application.id}, used to derive the changelog topic name
     * @param apicurioConfig Apicurio serde config with {@code apicurio.registry.*} prefixed keys;
     *                       when empty or missing {@code apicurio.registry.url}, falls back to plain JSON
     * @return an empty topology if no tenants are configured, otherwise the full diffuser topology
     */
    public static Topology build(AppConfig appConfig, String applicationId, Map<String, Object> apicurioConfig) {
        StreamsBuilder builder = new StreamsBuilder();

        TenantResolver tenantResolver = new TenantResolver(appConfig.lhClusterId());
        RecordRouter recordRouter = new RecordRouter(appConfig);
        InputTopicPatternBuilder patternBuilder = new InputTopicPatternBuilder(appConfig);

        log.debug("Building topology: executionPattern={}, metadataPattern={}",
                patternBuilder.executionTopicPattern(), patternBuilder.metadataTopicPattern());

        if (appConfig.tenants().isEmpty()) {
            return builder.build();
        }

        // --- Metadata path ---
        // Ingest raw metadata bytes from per-tenant metadata topics, re-key with tenant prefix,
        // and store in a local state store. Its automatic changelog feeds the GlobalKTable below.
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(MetadataProcessor.METADATA_STORE),
                Serdes.String(), Serdes.ByteArray()));

        builder.stream(patternBuilder.metadataTopicPattern(),
                        Consumed.<String, byte[]>as("metadata-source")
                                .withOffsetResetPolicy(AutoOffsetReset.earliest()) // always ensure all metadata is read
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.ByteArray()))
                .process(() -> new MetadataProcessor(tenantResolver),
                        Named.as("metadata-processor"),
                        MetadataProcessor.METADATA_STORE);

        // GlobalKTable reads from the metadata store's changelog and deserializes as protobuf,
        // making WfSpec metadata available to DiffuserProcessor on all partitions.
        String changelogTopic = applicationId + "-" + MetadataProcessor.METADATA_STORE + "-changelog";
        log.debug("Changelog topic for GlobalKTable: {}", changelogTopic);
        Serde<MetadataOutputTopicRecord> metadataSerde = ProtobufSerdes.metadataOutputTopicRecord();
        builder.globalTable(changelogTopic,
                Consumed.<String, MetadataOutputTopicRecord>as("global-metadata-source")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(metadataSerde),
                Materialized.<String, MetadataOutputTopicRecord, KeyValueStore<Bytes, byte[]>>as(DiffuserProcessor.GLOBAL_METADATA_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(metadataSerde));

        // --- Execution path ---
        // Process execution records (WfRun, Variable, UserTaskRun, ExternalEvent, WorkflowEvent),
        // transform to JSON, and route to dynamically resolved streamlet topics.
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(DiffuserProcessor.WFRUN_STORE),
                Serdes.String(), Serdes.ByteArray()));
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(DiffuserProcessor.WFRUN_EVICTION_STORE),
                Serdes.String(), Serdes.ByteArray()));

        Serde<JsonNode> valueSerde;
        if (apicurioConfig.containsKey("apicurio.registry.url")) {
            log.debug("Using Apicurio serde");
            valueSerde = ApicurioJsonNodeSerdeFactory.create(apicurioConfig);
        } else {
            log.debug("Using plain JSON serde (no schema registry configured)");
            valueSerde = Serdes.serdeFrom(new JsonNodeSerializer(), (topic, data) -> {
                throw new UnsupportedOperationException("Deserialization not supported");
            });
        }

        builder.stream(patternBuilder.executionTopicPattern(),
                        Consumed.<String, OutputTopicRecord>as("execution-source")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(ProtobufSerdes.outputTopicRecord()))
                .process(() -> new DiffuserProcessor(tenantResolver, recordRouter, appConfig.eviction()),
                        Named.as("diffuser-processor"),
                        DiffuserProcessor.WFRUN_STORE,
                        DiffuserProcessor.WFRUN_EVICTION_STORE)
                .to((key, value, ctx) -> StreamletTopicExtractor.extract(ctx),
                        Produced.<String, JsonNode>as("streamlet-sink")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(valueSerde));

        return builder.build();
    }
}
