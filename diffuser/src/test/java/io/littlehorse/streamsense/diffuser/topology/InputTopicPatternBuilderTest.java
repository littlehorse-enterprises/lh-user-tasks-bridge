package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

import static org.junit.jupiter.api.Assertions.*;

class InputTopicPatternBuilderTest {

    @Test
    void singleTenantExecutionPattern() {
        InputTopicPatternBuilder builder = builderFor("cluster1", "default");

        Pattern pattern = builder.executionTopicPattern();
        assertTrue(pattern.matcher("cluster1_default_execution").matches());
        assertFalse(pattern.matcher("cluster1_default_metadata").matches());
        assertFalse(pattern.matcher("cluster1_other_execution").matches());
    }

    @Test
    void singleTenantMetadataPattern() {
        InputTopicPatternBuilder builder = builderFor("cluster1", "default");

        Pattern pattern = builder.metadataTopicPattern();
        assertTrue(pattern.matcher("cluster1_default_metadata").matches());
        assertFalse(pattern.matcher("cluster1_default_execution").matches());
        assertFalse(pattern.matcher("cluster1_other_metadata").matches());
    }

    @Test
    void multiTenantAlternation() {
        InputTopicPatternBuilder builder = builderFor("cluster1", "tenant1", "tenant2");

        Pattern exec = builder.executionTopicPattern();
        assertTrue(exec.matcher("cluster1_tenant1_execution").matches());
        assertTrue(exec.matcher("cluster1_tenant2_execution").matches());
        assertFalse(exec.matcher("cluster1_tenant3_execution").matches());

        Pattern meta = builder.metadataTopicPattern();
        assertTrue(meta.matcher("cluster1_tenant1_metadata").matches());
        assertTrue(meta.matcher("cluster1_tenant2_metadata").matches());
        assertFalse(meta.matcher("cluster1_tenant3_metadata").matches());
    }

    @Test
    void specialCharsInClusterIdAreQuoted() {
        InputTopicPatternBuilder builder = builderFor("my.cluster+1", "default");

        Pattern pattern = builder.executionTopicPattern();
        // The literal dots and plus should NOT act as regex wildcards
        assertTrue(pattern.matcher("my.cluster+1_default_execution").matches());
        assertFalse(pattern.matcher("myXclusterX1_default_execution").matches());
    }

    @Test
    void specialCharsInTenantNameAreQuoted() {
        InputTopicPatternBuilder builder = builderFor("cluster1", "my.tenant+name");

        Pattern pattern = builder.executionTopicPattern();
        assertTrue(pattern.matcher("cluster1_my.tenant+name_execution").matches());
        assertFalse(pattern.matcher("cluster1_myXtenantXname_execution").matches());
    }

    // --- Helpers ---

    private static InputTopicPatternBuilder builderFor(String clusterId, String... tenantNames) {
        List<AppConfig.TenantConfig> tenants = java.util.Arrays.stream(tenantNames)
                .map(name -> (AppConfig.TenantConfig) new AppConfig.TenantConfig() {
                    @Override public String name() { return name; }
                    @Override public Optional<List<String>> wfSpecs() { return Optional.empty(); }
                    @Override public Optional<List<String>> userTaskDefs() { return Optional.empty(); }
                    @Override public Optional<List<String>> externalEventDefs() { return Optional.empty(); }
                    @Override public Optional<List<String>> workflowEventDefs() { return Optional.empty(); }
                })
                .toList();

        AppConfig config = new AppConfig() {
            @Override public String lhClusterId() { return clusterId; }
            @Override public ServerConfig server() { return () -> 8080; }
            @Override public List<TenantConfig> tenants() { return tenants; }
            @Override public EvictionConfig eviction() {
                return new EvictionConfig() {
                    @Override public long sweepIntervalSeconds() { return 60; }
                    @Override public long fallbackRetentionSeconds() { return 2592000; }
                };
            }
        };
        return new InputTopicPatternBuilder(config);
    }
}
