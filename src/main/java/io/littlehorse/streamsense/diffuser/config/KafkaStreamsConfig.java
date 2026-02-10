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
                String ksKey = name.substring(PREFIX.length());
                config.getOptionalValue(name, String.class)
                        .ifPresent(value -> properties.put(ksKey, value));
            }
        }
        // Ensure defaults
        properties.putIfAbsent("application.id", "lh-workflow-diffuser");
        properties.putIfAbsent("bootstrap.servers", "localhost:9092");
    }

    public String applicationId() {
        return properties.getProperty("application.id", "lh-workflow-diffuser");
    }

    public String bootstrapServers() {
        return properties.getProperty("bootstrap.servers", "localhost:9092");
    }

    public Properties asProperties() {
        return new Properties(properties);
    }
}
