package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.SmallRyeConfig;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

/**
 * Extracts all properties with a given prefix from {@link SmallRyeConfig},
 * strips the prefix, and unquotes SmallRye-escaped dots.
 *
 * <p>For example, with prefix {@code "apicurio.registry."}, a SmallRye property
 * named {@code apicurio.registry."auto-register"} becomes key {@code auto-register}.
 */
public class PrefixConfig {

    private final Map<String, String> entries;

    public PrefixConfig(SmallRyeConfig config, String prefix) {
        this.entries = new HashMap<>();
        for (String name : config.getPropertyNames()) {
            if (name.startsWith(prefix)) {
                String stripped = unquote(name.substring(prefix.length()));
                config.getOptionalValue(name, String.class)
                        .ifPresent(value -> entries.put(stripped, value));
            }
        }
    }

    public String get(String key) {
        return entries.get(key);
    }

    public String get(String key, String defaultValue) {
        return entries.getOrDefault(key, defaultValue);
    }

    public boolean has(String key) {
        return entries.containsKey(key);
    }

    public boolean isEmpty() {
        return entries.isEmpty();
    }

    /** Returns stripped keys as a {@link Properties} (e.g. for Kafka Streams). */
    public Properties asProperties() {
        Properties props = new Properties();
        props.putAll(entries);
        return props;
    }

    /**
     * Returns entries with the original prefix re-applied (e.g. for Apicurio serdes config).
     *
     * @param prefix the prefix to prepend to each key
     */
    public Map<String, Object> asPrefixedMap(String prefix) {
        Map<String, Object> map = new HashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            map.put(prefix + entry.getKey(), entry.getValue());
        }
        return map;
    }

    /** SmallRye YAML quotes dotted keys (e.g. {@code "application.id"}) — strip the quotes. */
    static String unquote(String key) {
        if (key.startsWith("\"") && key.endsWith("\"")) {
            return key.substring(1, key.length() - 1);
        }
        return key;
    }
}
