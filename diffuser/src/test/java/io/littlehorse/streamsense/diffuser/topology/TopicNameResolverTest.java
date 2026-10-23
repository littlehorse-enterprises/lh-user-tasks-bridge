package io.littlehorse.streamsense.diffuser.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class TopicNameResolverTest {

    private final TopicNameResolver resolver = new TopicNameResolver("cluster1");

    @Test
    void resolvesWfSpecTopic() {
        assertEquals("cluster1_tenant1_streamlet_workflow-order-processing",
                resolver.resolve("tenant1", StreamletType.WORKFLOW, "order-processing"));
    }

    @Test
    void resolvesUserTaskDefTopic() {
        assertEquals("cluster1_tenant1_streamlet_usertask-confirm-purchase",
                resolver.resolve("tenant1", StreamletType.USERTASK, "confirm-purchase"));
    }

    @Test
    void resolvesExternalEventDefTopic() {
        assertEquals("cluster1_tenant1_streamlet_externalevent-payment-confirmed",
                resolver.resolve("tenant1", StreamletType.EXTERNALEVENT, "payment-confirmed"));
    }

    @Test
    void resolvesWorkflowEventDefTopic() {
        assertEquals("cluster1_tenant1_streamlet_workflowevent-order-shipped",
                resolver.resolve("tenant1", StreamletType.WORKFLOWEVENT, "order-shipped"));
    }
}
