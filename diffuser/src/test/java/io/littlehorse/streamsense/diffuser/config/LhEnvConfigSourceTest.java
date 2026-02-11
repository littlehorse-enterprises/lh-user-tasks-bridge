package io.littlehorse.streamsense.diffuser.config;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LhEnvConfigSourceTest {

    @Test
    void stripsLhPrefixAndConvertsToDotNotation() {
        Map<String, String> env = Map.of(
                "LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS", "broker:9092",
                "LH_APP_SERVER_PORT", "9090"
        );

        LhEnvConfigSource source = new LhEnvConfigSource(env);

        assertEquals("broker:9092", source.getValue("kafka.streams.bootstrap.servers"));
        assertEquals("9090", source.getValue("app.server.port"));
    }

    @Test
    void ignoresNonLhPrefixedVariables() {
        Map<String, String> env = Map.of(
                "PATH", "/usr/bin",
                "LH_APP_LH_CLUSTER_ID", "test-cluster"
        );

        LhEnvConfigSource source = new LhEnvConfigSource(env);

        assertNull(source.getValue("path"));
        assertEquals("test-cluster", source.getValue("app.lh.cluster.id"));
    }

    @Test
    void hasCorrectOrdinal() {
        LhEnvConfigSource source = new LhEnvConfigSource(Map.of());
        assertEquals(400, source.getOrdinal());
    }

    @Test
    void hasCorrectName() {
        LhEnvConfigSource source = new LhEnvConfigSource(Map.of());
        assertEquals("LhEnvConfigSource", source.getName());
    }
}
