package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.serde.ProtobufSerdes;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.Topology;
import org.apache.kafka.streams.state.Stores;

import java.util.regex.Pattern;

/**
 * Builds the complete Kafka Streams topology with two sub-topologies:
 * <ul>
 *   <li>Sub-topology 0: Metadata ingestion → local state store → changelog → GlobalKTable</li>
 *   <li>Sub-topology 1: Execution processing → diffusion → dynamic sink</li>
 * </ul>
 */
public class TopologyFactory {

    public static Topology build(AppConfig appConfig, String applicationId) {
        Topology topology = new Topology();

        TenantResolver tenantResolver = new TenantResolver(appConfig.lhClusterId());
        RecordRouter recordRouter = new RecordRouter(appConfig);
        InputTopicPatternBuilder patternBuilder = new InputTopicPatternBuilder(appConfig);

        if (appConfig.tenants().isEmpty()) {
            return topology;
        }

        buildMetadataSubTopology(topology, patternBuilder.metadataTopicPattern(), tenantResolver, applicationId);
        buildDiffuserSubTopology(topology, patternBuilder.executionTopicPattern(), tenantResolver, recordRouter);

        return topology;
    }

    /**
     * Sub-topology 0: Metadata Processor
     *
     * <p>tenant-metadata-source → MetadataProcessor → "global-metadata" store (with automatic changelog)
     * Then a GlobalKTable reads from the automatic changelog for "global-metadata-store".</p>
     */
    private static void buildMetadataSubTopology(Topology topology,
                                                  Pattern metadataPattern,
                                                  TenantResolver tenantResolver,
                                                  String applicationId) {
        var metadataSerde = ProtobufSerdes.metadataOutputTopicRecord();
        String changelogTopic = applicationId + "-" + MetadataProcessor.METADATA_STORE + "-changelog";

        // Source: metadata topics
        topology.addSource(
                "tenant-metadata-source",
                Serdes.String().deserializer(),
                metadataSerde.deserializer(),
                metadataPattern
        );

        // Processor: resolves tenant, prefixes key, stores in state store
        topology.addProcessor(
                "metadata-processor",
                () -> new MetadataProcessor(tenantResolver),
                "tenant-metadata-source"
        );

        // State store for metadata (connected to metadata-processor)
        // Its automatic changelog feeds the GlobalKTable below
        topology.addStateStore(
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(MetadataProcessor.METADATA_STORE),
                        Serdes.String(),
                        Serdes.ByteArray()
                ),
                "metadata-processor"
        );

        // GlobalKTable: reads from the store's automatic changelog topic
        topology.addGlobalStore(
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore("global-metadata-store"),
                        Serdes.String(),
                        Serdes.ByteArray()
                ),
                "global-metadata-source",
                Serdes.String().deserializer(),
                Serdes.ByteArray().deserializer(),
                changelogTopic,
                "global-metadata-processor",
                () -> new GlobalStoreUpdater("global-metadata-store")
        );
    }

    /**
     * Sub-topology 1: Diffuser Processor
     *
     * <p>execution-source → DiffuserProcessor → streamlet-sink (dynamic topic via TopicNameExtractor)</p>
     */
    private static void buildDiffuserSubTopology(Topology topology,
                                                  Pattern executionPattern,
                                                  TenantResolver tenantResolver,
                                                  RecordRouter recordRouter) {
        var executionSerde = ProtobufSerdes.outputTopicRecord();

        // Source: execution topics
        topology.addSource(
                "execution-source",
                Serdes.String().deserializer(),
                executionSerde.deserializer(),
                executionPattern
        );

        // Processor: the core diffusion logic
        topology.addProcessor(
                "diffuser-processor",
                () -> new DiffuserProcessor(tenantResolver, recordRouter),
                "execution-source"
        );

        // Local state store for WfRun aggregation (connected to diffuser-processor)
        topology.addStateStore(
                Stores.keyValueStoreBuilder(
                        Stores.persistentKeyValueStore(DiffuserProcessor.WFRUN_STORE),
                        Serdes.String(),
                        Serdes.ByteArray()
                ),
                "diffuser-processor"
        );

        // Sink: dynamic topic routing via TopicNameExtractor (reads streamlet-topic header)
        topology.addSink(
                "streamlet-sink",
                (key, value, recordContext) -> StreamletTopicExtractor.extract(recordContext),
                Serdes.String().serializer(),
                new JsonNodeSerializer(),
                "diffuser-processor"
        );
    }
}
