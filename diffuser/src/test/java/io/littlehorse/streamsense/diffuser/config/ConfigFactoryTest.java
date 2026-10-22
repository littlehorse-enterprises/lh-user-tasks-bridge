package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.SmallRyeConfig;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ConfigFactoryTest {

    @Test
    void buildsConfigWithDefaults() {
        SmallRyeConfig config = ConfigFactory.build();
        AppConfig appConfig = config.getConfigMapping(AppConfig.class);

        assertEquals("cluster1", appConfig.lhClusterId());
        assertEquals(8080, appConfig.server().port());
        assertFalse(config.getOptionalValue("spring.application.name", String.class).isPresent(),
                "Diffuser configuration must not load the host application YAML");
        assertNotNull(appConfig.tenants());
    }

    @Test
    void buildsKafkaStreamsConfig() {
        SmallRyeConfig config = ConfigFactory.build();
        KafkaStreamsConfig ksConfig = new KafkaStreamsConfig(config);

        assertEquals("cluster1-wf-diffuser", ksConfig.applicationId());
        assertEquals("localhost:9092", ksConfig.bootstrapServers());
        assertFalse(ksConfig.asProperties().isEmpty(), "asProperties() must contain entries, not just defaults");
    }
}
