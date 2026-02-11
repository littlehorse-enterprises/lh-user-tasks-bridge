package io.littlehorse.streamsense.diffuser.topology;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Processor for sub-topology 0: ingests raw metadata bytes from metadata topics,
 * resolves tenant from the topic name, prefixes the key with tenant, and stores
 * as-is in the local state store. The store's automatic changelog feeds the GlobalKTable.
 */
public class MetadataProcessor implements Processor<String, byte[], Void, Void> {

    private static final Logger log = LoggerFactory.getLogger(MetadataProcessor.class);

    public static final String METADATA_STORE = "global-metadata";

    private final TenantResolver tenantResolver;
    private ProcessorContext<Void, Void> context;
    private KeyValueStore<String, byte[]> metadataStore;

    public MetadataProcessor(TenantResolver tenantResolver) {
        this.tenantResolver = tenantResolver;
    }

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        this.context = context;
        this.metadataStore = context.getStateStore(METADATA_STORE);
    }

    @Override
    public void process(Record<String, byte[]> record) {
        if (record.value() == null) {
            return;
        }

        try {
            String topic = context.recordMetadata().get().topic();
            String tenant = tenantResolver.resolve(topic);
            String prefixedKey = tenant + "/" + record.key();

            // Store raw bytes as-is; automatic changelog feeds the GlobalKTable
            metadataStore.put(prefixedKey, record.value());

            log.debug("Stored metadata: key={}, tenant={}", prefixedKey, tenant);
        } catch (Exception e) {
            log.error("Error processing metadata record: key={}", record.key(), e);
        }
    }
}
