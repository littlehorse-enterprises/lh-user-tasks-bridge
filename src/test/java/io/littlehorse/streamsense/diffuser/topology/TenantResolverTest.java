package io.littlehorse.streamsense.diffuser.topology;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TenantResolverTest {

    private final TenantResolver resolver = new TenantResolver("cluster1");

    @Test
    void resolvesExecutionTopic() {
        assertEquals("tenant1", resolver.resolve("cluster1_tenant1_execution"));
    }

    @Test
    void resolvesMetadataTopic() {
        assertEquals("tenant1", resolver.resolve("cluster1_tenant1_metadata"));
    }

    @Test
    void resolvesTenantWithUnderscores() {
        assertEquals("my_tenant", resolver.resolve("cluster1_my_tenant_execution"));
    }

    @Test
    void throwsOnInvalidPrefix() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("wrong_tenant1_execution"));
    }

    @Test
    void throwsOnMissingSuffix() {
        assertThrows(IllegalArgumentException.class,
                () -> resolver.resolve("cluster1_nosuffix"));
    }
}
