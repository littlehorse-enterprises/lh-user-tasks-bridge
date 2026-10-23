package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.SmallRyeConfig;

import java.util.Properties;

/**
 * Extracts Kafka Streams properties from SmallRyeConfig.
 * All properties under the {@code kafka.streams.*} prefix are extracted
 * and forwarded to Kafka Streams as a Properties object.
 */
public class KafkaStreamsConfig {

    private static final String PREFIX = "kafka.streams.";

    private final PrefixConfig delegate;

    public KafkaStreamsConfig(SmallRyeConfig config) {
        this.delegate = new PrefixConfig(config, PREFIX);
    }

    public String applicationId() {
        return delegate.get("application.id");
    }

    public String bootstrapServers() {
        return delegate.get("bootstrap.servers", "localhost:9092");
    }

    public String schemaRegistryUrl() {
        return delegate.get("schema.registry.url");
    }

    public Properties asProperties() {
        return delegate.asProperties();
    }
}
