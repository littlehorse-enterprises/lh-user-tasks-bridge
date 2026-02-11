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
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.Materialized;
import org.apache.kafka.streams.kstream.Named;
import org.apache.kafka.streams.kstream.Produced;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.Stores;

/**
 * Builds the complete Kafka Streams topology using the StreamsBuilder DSL:
 * <ul>
 *   <li>Metadata ingestion → local state store → changelog → GlobalKTable</li>
 *   <li>Execution processing → diffusion → dynamic sink</li>
 * </ul>
 */
public class TopologyFactory {

    /**
     * @param schemaRegistryUrl Apicurio registry URL; when {@code null}, falls back to plain JSON serialization.
     */
    public static Topology build(AppConfig appConfig, String applicationId, String schemaRegistryUrl) {
        StreamsBuilder builder = new StreamsBuilder();

        TenantResolver tenantResolver = new TenantResolver(appConfig.lhClusterId());
        RecordRouter recordRouter = new RecordRouter(appConfig);
        InputTopicPatternBuilder patternBuilder = new InputTopicPatternBuilder(appConfig);

        if (appConfig.tenants().isEmpty()) {
            return builder.build();
        }

        // --- Metadata ingestion ---
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(MetadataProcessor.METADATA_STORE),
                Serdes.String(), Serdes.ByteArray()));

        builder.stream(patternBuilder.metadataTopicPattern(),
                        Consumed.<String, byte[]>as("metadata-source")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(Serdes.ByteArray()))
                .process(() -> new MetadataProcessor(tenantResolver),
                        Named.as("metadata-processor"),
                        MetadataProcessor.METADATA_STORE);

        String changelogTopic = applicationId + "-" + MetadataProcessor.METADATA_STORE + "-changelog";
        Serde<MetadataOutputTopicRecord> metadataSerde = ProtobufSerdes.metadataOutputTopicRecord();
        builder.globalTable(changelogTopic,
                Consumed.<String, MetadataOutputTopicRecord>as("global-metadata-source")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(metadataSerde),
                Materialized.<String, MetadataOutputTopicRecord, KeyValueStore<Bytes, byte[]>>as(DiffuserProcessor.GLOBAL_METADATA_STORE)
                        .withKeySerde(Serdes.String())
                        .withValueSerde(metadataSerde));

        // --- Execution diffusion ---
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(DiffuserProcessor.WFRUN_STORE),
                Serdes.String(), Serdes.ByteArray()));

        Serde<JsonNode> valueSerde = schemaRegistryUrl != null
                ? ApicurioJsonNodeSerdeFactory.create(schemaRegistryUrl)
                : Serdes.serdeFrom(new JsonNodeSerializer(), (topic, data) -> {
            throw new UnsupportedOperationException("Deserialization not supported");
        });

        builder.stream(patternBuilder.executionTopicPattern(),
                        Consumed.<String, OutputTopicRecord>as("execution-source")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(ProtobufSerdes.outputTopicRecord()))
                .process(() -> new DiffuserProcessor(tenantResolver, recordRouter),
                        Named.as("diffuser-processor"),
                        DiffuserProcessor.WFRUN_STORE)
                .to((key, value, ctx) -> StreamletTopicExtractor.extract(ctx),
                        Produced.<String, JsonNode>as("streamlet-sink")
                                .withKeySerde(Serdes.String())
                                .withValueSerde(valueSerde));

        return builder.build();
    }
}
