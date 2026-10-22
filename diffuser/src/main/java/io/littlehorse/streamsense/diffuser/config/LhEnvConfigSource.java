package io.littlehorse.streamsense.diffuser.config;

import org.eclipse.microprofile.config.spi.ConfigSource;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

/**
 * Custom ConfigSource that reads environment variables with the {@code LH_} prefix,
 * strips the prefix, and converts underscores to dots for property name mapping.
 *
 * <p>Example: {@code LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS=broker:9092}
 * becomes property {@code kafka.streams.bootstrap.servers=broker:9092}</p>
 */
public class LhEnvConfigSource implements ConfigSource {

    private static final String PREFIX = "LH_";
    private static final int ORDINAL = 400;

    private final Map<String, String> properties;

    public LhEnvConfigSource() {
        this(System.getenv());
    }

    // Visible for testing
    LhEnvConfigSource(Map<String, String> env) {
        this.properties = mapFromEnv(env);
    }

    private static Map<String, String> mapFromEnv(Map<String, String> env) {
        Map<String, String> result = new HashMap<>();
        for (Map.Entry<String, String> entry : env.entrySet()) {
            String key = entry.getKey();
            if (key.startsWith(PREFIX)) {
                String stripped = key.substring(PREFIX.length());
                String dotted = stripped.toLowerCase().replace('_', '.');
                result.put(dotted, entry.getValue());
            }
        }
        return result;
    }

    @Override
    public Set<String> getPropertyNames() {
        return properties.keySet();
    }

    @Override
    public String getValue(String propertyName) {
        return properties.get(propertyName);
    }

    @Override
    public String getName() {
        return "LhEnvConfigSource";
    }

    @Override
    public int getOrdinal() {
        return ORDINAL;
    }
}
