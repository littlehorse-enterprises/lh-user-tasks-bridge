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

    private final Properties properties;

    public KafkaStreamsConfig(SmallRyeConfig config) {
        this.properties = new Properties();
        for (String name : config.getPropertyNames()) {
            if (name.startsWith(PREFIX)) {
                String ksKey = unquote(name.substring(PREFIX.length()));
                config.getOptionalValue(name, String.class)
                        .ifPresent(value -> properties.put(ksKey, value));
            }
        }
    }

    public String applicationId() {
        return properties.getProperty("application.id");
    }

    public String bootstrapServers() {
        return properties.getProperty("bootstrap.servers", "localhost:9092");
    }

    public String schemaRegistryUrl() {
        return properties.getProperty("schema.registry.url");
    }

    public Properties asProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }

    /** SmallRye YAML quotes dotted keys (e.g. {@code "application.id"}) — strip the quotes. */
    private static String unquote(String key) {
        if (key.startsWith("\"") && key.endsWith("\"")) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }
}
