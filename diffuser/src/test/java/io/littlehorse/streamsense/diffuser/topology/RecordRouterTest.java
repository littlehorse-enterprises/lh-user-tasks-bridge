package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

class RecordRouterTest {

    private static final String CLUSTER_ID = "cluster1";

    @Test
    void configuredWfSpecResolvesToCorrectTopic() {
        RecordRouter router = new RecordRouter(configWith(
                tenant("default",
                        Optional.of(List.of("order-processing")),
                        Optional.empty(), Optional.empty(), Optional.empty())));

        Optional<String> topic = router.resolveWfSpecTopic("default", "order-processing");
        assertTrue(topic.isPresent());
        assertEquals("cluster1_default_streamlet_workflow-order-processing", topic.get());
    }

    @Test
    void unconfiguredWfSpecReturnsEmpty() {
        RecordRouter router = new RecordRouter(configWith(
                tenant("default",
                        Optional.of(List.of("order-processing")),
                        Optional.empty(), Optional.empty(), Optional.empty())));

        assertTrue(router.resolveWfSpecTopic("default", "unknown-spec").isEmpty());
    }

    @Test
    void unknownTenantReturnsEmpty() {
        RecordRouter router = new RecordRouter(configWith(
                tenant("default",
                        Optional.of(List.of("order-processing")),
                        Optional.empty(), Optional.empty(), Optional.empty())));

        assertTrue(router.resolveWfSpecTopic("other-tenant", "order-processing").isEmpty());
    }

    @Test
    void allFourStreamletTypesRouteCorrectly() {
        RecordRouter router = new RecordRouter(configWith(
                tenant("t1",
                        Optional.of(List.of("my-wf")),
                        Optional.of(List.of("my-ut")),
                        Optional.of(List.of("my-ee")),
                        Optional.of(List.of("my-we")))));

        assertEquals("cluster1_t1_streamlet_workflow-my-wf",
                router.resolveWfSpecTopic("t1", "my-wf").orElseThrow());
        assertEquals("cluster1_t1_streamlet_usertask-my-ut",
                router.resolveUserTaskDefTopic("t1", "my-ut").orElseThrow());
        assertEquals("cluster1_t1_streamlet_externalevent-my-ee",
                router.resolveExternalEventDefTopic("t1", "my-ee").orElseThrow());
        assertEquals("cluster1_t1_streamlet_workflowevent-my-we",
                router.resolveWorkflowEventDefTopic("t1", "my-we").orElseThrow());
    }

    @Test
    void tenantWithNoSpecsOfGivenTypeReturnsEmpty() {
        RecordRouter router = new RecordRouter(configWith(
                tenant("default",
                        Optional.of(List.of("order-processing")),
                        Optional.empty(), Optional.empty(), Optional.empty())));

        assertTrue(router.resolveUserTaskDefTopic("default", "some-task").isEmpty());
        assertTrue(router.resolveExternalEventDefTopic("default", "some-event").isEmpty());
        assertTrue(router.resolveWorkflowEventDefTopic("default", "some-event").isEmpty());
    }

    // --- Config helpers ---

    private static AppConfig configWith(AppConfig.TenantConfig... tenants) {
        return new AppConfig() {
            @Override public String lhClusterId() { return CLUSTER_ID; }
            @Override public ServerConfig server() { return () -> 8080; }
            @Override public List<TenantConfig> tenants() { return List.of(tenants); }
            @Override public EvictionConfig eviction() {
                return new EvictionConfig() {
                    @Override public long sweepIntervalSeconds() { return 60; }
                    @Override public long fallbackRetentionSeconds() { return 2592000; }
                };
            }
        };
    }

    private static AppConfig.TenantConfig tenant(String name,
                                                  Optional<List<String>> wfSpecs,
                                                  Optional<List<String>> userTaskDefs,
                                                  Optional<List<String>> externalEventDefs,
                                                  Optional<List<String>> workflowEventDefs) {
        return new AppConfig.TenantConfig() {
            @Override public String name() { return name; }
            @Override public Optional<List<String>> wfSpecs() { return wfSpecs; }
            @Override public Optional<List<String>> userTaskDefs() { return userTaskDefs; }
            @Override public Optional<List<String>> externalEventDefs() { return externalEventDefs; }
            @Override public Optional<List<String>> workflowEventDefs() { return workflowEventDefs; }
        };
    }
}
