package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.sdk.common.proto.MetadataOutputTopicRecord;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Simple processor that updates the global state store from the metadata changelog topic.
 * Used as the processor for the GlobalKTable's addGlobalStore call.
 */
public class GlobalStoreUpdater implements Processor<String, MetadataOutputTopicRecord, Void, Void> {

    private final String storeName;
    private KeyValueStore<String, byte[]> store;

    public GlobalStoreUpdater(String storeName) {
        this.storeName = storeName;
    }

    @Override
    public void init(ProcessorContext<Void, Void> context) {
        this.store = context.getStateStore(storeName);
    }

    @Override
    public void process(Record<String, MetadataOutputTopicRecord> record) {
        if (record.value() == null) {
            store.delete(record.key());
        } else {
            store.put(record.key(), record.value().toByteArray());
        }
    }
}
