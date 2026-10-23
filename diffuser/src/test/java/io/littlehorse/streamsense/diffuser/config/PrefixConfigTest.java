package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.PropertiesConfigSource;
import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.junit.jupiter.api.Test;

import java.util.Map;
import java.util.Properties;

import static org.junit.jupiter.api.Assertions.*;

class PrefixConfigTest {

    private static SmallRyeConfig configWith(Map<String, String> entries) {
        return new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(entries, "test", 100))
                .build();
    }

    @Test
    void extractsPropertiesWithPrefix() {
        SmallRyeConfig config = configWith(Map.of(
                "apicurio.registry.url", "http://localhost:8085",
                "apicurio.registry.auto-register", "true",
                "other.key", "ignored"
        ));

        PrefixConfig pc = new PrefixConfig(config, "apicurio.registry.");

        assertEquals("http://localhost:8085", pc.get("url"));
        assertEquals("true", pc.get("auto-register"));
        assertNull(pc.get("other.key"));
    }

    @Test
    void getWithDefault() {
        SmallRyeConfig config = configWith(Map.of(
                "prefix.existing", "value"
        ));

        PrefixConfig pc = new PrefixConfig(config, "prefix.");

        assertEquals("value", pc.get("existing", "fallback"));
        assertEquals("fallback", pc.get("missing", "fallback"));
    }

    @Test
    void hasKey() {
        SmallRyeConfig config = configWith(Map.of(
                "prefix.present", "yes"
        ));

        PrefixConfig pc = new PrefixConfig(config, "prefix.");

        assertTrue(pc.has("present"));
        assertFalse(pc.has("absent"));
    }

    @Test
    void emptyConfig() {
        SmallRyeConfig config = configWith(Map.of());

        PrefixConfig pc = new PrefixConfig(config, "apicurio.registry.");

        assertTrue(pc.isEmpty());
        assertNull(pc.get("url"));
    }

    @Test
    void asPropertiesReturnsStrippedKeys() {
        SmallRyeConfig config = configWith(Map.of(
                "kafka.streams.bootstrap.servers", "localhost:9092",
                "kafka.streams.application.id", "my-app"
        ));

        PrefixConfig pc = new PrefixConfig(config, "kafka.streams.");
        Properties props = pc.asProperties();

        assertEquals("localhost:9092", props.getProperty("bootstrap.servers"));
        assertEquals("my-app", props.getProperty("application.id"));
    }

    @Test
    void asPrefixedMapReAppliesPrefix() {
        SmallRyeConfig config = configWith(Map.of(
                "apicurio.registry.url", "http://host:8085",
                "apicurio.registry.auto-register", "true"
        ));

        PrefixConfig pc = new PrefixConfig(config, "apicurio.registry.");
        Map<String, Object> map = pc.asPrefixedMap("apicurio.registry.");

        assertEquals("http://host:8085", map.get("apicurio.registry.url"));
        assertEquals("true", map.get("apicurio.registry.auto-register"));
        assertEquals(2, map.size());
    }

    @Test
    void higherPriorityQuotedYamlOverridesDottedProperties() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new PropertiesConfigSource(Map.of(
                        "kafka.streams.bootstrap.servers", "low-broker:9092",
                        "kafka.streams.producer.linger.ms", "5"), "low", 100))
                .withSources(new YamlConfigSource("high-yaml", """
                        kafka:
                          streams:
                            bootstrap.servers: high-broker:9092
                            producer.linger.ms: 25
                        """, 300))
                .build();

        PrefixConfig pc = new PrefixConfig(config, "kafka.streams.");
        assertEquals("high-broker:9092", pc.get("bootstrap.servers"));
        assertEquals("25", pc.asProperties().getProperty("producer.linger.ms"));
    }

    @Test
    void unquoteStripsSmallRyeQuotes() {
        assertEquals("application.id", PrefixConfig.unquote("\"application.id\""));
        assertEquals("plain-key", PrefixConfig.unquote("plain-key"));
        assertEquals("", PrefixConfig.unquote("\"\""));
    }
}
