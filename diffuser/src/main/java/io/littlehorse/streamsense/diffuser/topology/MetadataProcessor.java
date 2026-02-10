package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.sdk.common.proto.MetadataOutputTopicRecord;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for sub-topology 0: ingests metadata records from metadata topics,
 * resolves tenant from the topic name, prefixes the key with tenant, and stores
 * in the local state store. The store's changelog feeds the GlobalKTable.
 */
public class MetadataProcessor implements Processor<String, MetadataOutputTopicRecord, String, MetadataOutputTopicRecord> {

    private static final Logger log = LoggerFactory.getLogger(MetadataProcessor.class);

    public static final String METADATA_STORE = "tenant-metadata-store";

    private final TenantResolver tenantResolver;
    private ProcessorContext<String, MetadataOutputTopicRecord> context;
    private KeyValueStore<String, byte[]> metadataStore;

    public MetadataProcessor(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(ProcessorContext<String, MetadataOutputTopicRecord> context) {
        this.context = context;
        this.metadataStore = context.getStateStore(METADATA_STORE);
    }

    @Override
    public void process(Record<String, MetadataOutputTopicRecord> record) {
        if (record.value() == null) {
            return;
        }

        try {
            String topic = context.recordMetadata().get().topic();
            String tenant = tenantResolver.resolve(topic);
            String prefixedKey = tenant + "/" + record.key();

            // Store the serialized protobuf bytes
            metadataStore.put(prefixedKey, record.value().toByteArray());

            // Forward with prefixed key for the changelog → GlobalKTable
            context.forward(new Record<>(prefixedKey, record.value(), record.timestamp()));

            log.debug("Stored metadata: key={}, tenant={}", prefixedKey, tenant);
        } catch (Exception e) {
            log.error("Error processing metadata record: key={}", record.key(), e);
        }
    }
}
