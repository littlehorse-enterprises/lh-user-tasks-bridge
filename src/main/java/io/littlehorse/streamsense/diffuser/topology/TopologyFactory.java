package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
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

import com.fasterxml.jackson.databind.JsonNode;

/**
 * Builds the complete Kafka Streams topology using the StreamsBuilder DSL:
 * <ul>
 *   <li>Metadata ingestion → local state store → changelog → GlobalKTable</li>
 *   <li>Execution processing → diffusion → dynamic sink</li>
 * </ul>
 */
public class TopologyFactory {

    public static Topology build(AppConfig appConfig, String applicationId) {
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
                        Consumed.with(Serdes.String(), ProtobufSerdes.metadataOutputTopicRecord()))
                .process(() -> new MetadataProcessor(tenantResolver),
                        Named.as("metadata-processor"),
                        MetadataProcessor.METADATA_STORE);

        String changelogTopic = applicationId + "-" + MetadataProcessor.METADATA_STORE + "-changelog";
        builder.globalTable(changelogTopic,
                Consumed.with(Serdes.String(), Serdes.ByteArray()),
                Materialized.<String, byte[], KeyValueStore<Bytes, byte[]>>as("global-metadata-store")
                        .withKeySerde(Serdes.String())
                        .withValueSerde(Serdes.ByteArray()));

        // --- Execution diffusion ---
        builder.addStateStore(Stores.keyValueStoreBuilder(
                Stores.persistentKeyValueStore(DiffuserProcessor.WFRUN_STORE),
                Serdes.String(), Serdes.ByteArray()));

        builder.stream(patternBuilder.executionTopicPattern(),
                        Consumed.with(Serdes.String(), ProtobufSerdes.outputTopicRecord()))
                .process(() -> new DiffuserProcessor(tenantResolver, recordRouter),
                        Named.as("diffuser-processor"),
                        DiffuserProcessor.WFRUN_STORE)
                .to((key, value, ctx) -> StreamletTopicExtractor.extract(ctx),
                        Produced.with(Serdes.String(), jsonNodeSerde()));

        return builder.build();
    }

    private static Serde<JsonNode> jsonNodeSerde() {
        return Serdes.serdeFrom(new JsonNodeSerializer(), (topic, data) -> {
            throw new UnsupportedOperationException("Deserialization not supported");
        });
    }
}
