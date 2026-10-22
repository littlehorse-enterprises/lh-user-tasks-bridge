package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.SmallRyeConfig;
import io.smallrye.config.SmallRyeConfigBuilder;
import io.smallrye.config.source.yaml.YamlConfigSource;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LhEnvConfigSourceTest {

    @Test
    void stripsLhPrefixAndUsesNativeEnvironmentLookup() {
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
        assertEquals("test-cluster", source.getValue("app.lh-cluster-id"));
    }

    @Test
    void mapsTypedKebabCaseAndIndexedTenantConfigurationFromEnvironmentOnly() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new LhEnvConfigSource(Map.ofEntries(
                        Map.entry("LH_APP_LH_CLUSTER_ID", "environment-cluster"),
                        Map.entry("LH_APP_SERVER_PORT", "9090"),
                        Map.entry("LH_APP_EVICTION_SWEEP_INTERVAL_SECONDS", "15"),
                        Map.entry("LH_APP_EVICTION_FALLBACK_RETENTION_SECONDS", "3600"),
                        Map.entry("LH_APP_TENANTS_0__NAME", "first"),
                        Map.entry("LH_APP_TENANTS_0__WF_SPECS", "order,shipping"),
                        Map.entry("LH_APP_TENANTS_0__USER_TASK_DEFS_0_", "approval"),
                        Map.entry("LH_APP_TENANTS_0__EXTERNAL_EVENT_DEFS", "payment"),
                        Map.entry("LH_APP_TENANTS_0__WORKFLOW_EVENT_DEFS", "completed"),
                        Map.entry("LH_APP_TENANTS_1__NAME", "second"),
                        Map.entry("LH_APP_TENANTS_1__WF_SPECS_0_", "refund"))))
                .withMapping(AppConfig.class)
                .withValidateUnknown(false)
                .build();

        AppConfig app = config.getConfigMapping(AppConfig.class);
        assertEquals("environment-cluster", app.lhClusterId());
        assertEquals(9090, app.server().port());
        assertEquals(15, app.eviction().sweepIntervalSeconds());
        assertEquals(3600, app.eviction().fallbackRetentionSeconds());
        assertEquals(2, app.tenants().size());
        AppConfig.TenantConfig first = app.tenants().get(0);
        assertEquals("first", first.name());
        assertEquals(List.of("order", "shipping"), first.wfSpecs().orElseThrow());
        assertEquals(List.of("approval"), first.userTaskDefs().orElseThrow());
        assertEquals(List.of("payment"), first.externalEventDefs().orElseThrow());
        assertEquals(List.of("completed"), first.workflowEventDefs().orElseThrow());
        assertEquals("second", app.tenants().get(1).name());
        assertEquals(List.of("refund"), app.tenants().get(1).wfSpecs().orElseThrow());
    }

    @Test
    void overridesYamlAndPreservesKafkaPassThroughProperties() {
        SmallRyeConfig config = new SmallRyeConfigBuilder()
                .withSources(new YamlConfigSource("test-yaml", """
                        app:
                          lh-cluster-id: file-cluster
                          tenants:
                            - name: file-tenant
                              wf-specs: [file-workflow]
                        kafka:
                          streams:
                            bootstrap.servers: file-broker:9092
                        """, 300))
                .withSources(new LhEnvConfigSource(Map.of(
                        "LH_APP_LH_CLUSTER_ID", "env-cluster",
                        "LH_APP_TENANTS_0__NAME", "env-tenant",
                        "LH_APP_TENANTS_0__WF_SPECS_0_", "env-workflow",
                        "LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS", "env-broker:9092",
                        "LH_KAFKA_STREAMS_PRODUCER_LINGER_MS", "25",
                        "APP_SERVER_PORT", "9999")))
                .withMapping(AppConfig.class)
                .withValidateUnknown(false)
                .build();

        AppConfig app = config.getConfigMapping(AppConfig.class);
        assertEquals("env-cluster", app.lhClusterId());
        assertEquals("env-tenant", app.tenants().getFirst().name());
        assertEquals(List.of("env-workflow"), app.tenants().getFirst().wfSpecs().orElseThrow());
        assertEquals(8080, app.server().port());
        KafkaStreamsConfig kafka = new KafkaStreamsConfig(config);
        assertEquals("env-broker:9092", kafka.bootstrapServers());
        assertEquals("env-broker:9092", kafka.asProperties().getProperty("bootstrap.servers"));
        assertEquals("25", kafka.asProperties().getProperty("producer.linger.ms"));
    }

    @Test
    void hasCorrectOrdinal() {
        LhEnvConfigSource source = new LhEnvConfigSource(Map.of());
        assertEquals(400, source.getOrdinal());
        assertEquals(400, new LhEnvConfigSource(Map.of("LH_CONFIG_ORDINAL", "999")).getOrdinal());
    }

    @Test
    void hasCorrectName() {
        LhEnvConfigSource source = new LhEnvConfigSource(Map.of());
        assertEquals("LhEnvConfigSource", source.getName());
    }
}
