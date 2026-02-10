package io.littlehorse.streamsense.diffuser.topology;

import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;

/**
 * Simple processor that updates the global state store from the automatic changelog topic.
 * The changelog carries raw {@code byte[]} (the store's value serde), not protobuf objects.
 */
public class GlobalStoreUpdater implements Processor<String, byte[], Void, Void> {

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
    public void process(Record<String, byte[]> record) {
        if (record.value() == null) {
            store.delete(record.key());
        } else {
            store.put(record.key(), record.value());
        }
    }
}
