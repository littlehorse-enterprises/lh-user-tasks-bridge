package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

/**
 * Application configuration mapped from the {@code app.*} config namespace.
 *
 * <p>Defines the LH cluster identity, HTTP server settings, and per-tenant
 * streamlet configuration that controls which metadata specs are diffused.
 */
@ConfigMapping(prefix = "app")
public interface AppConfig {

    /** LittleHorse cluster identifier, used as a prefix in Kafka topic names. */
    String lhClusterId();

    ServerConfig server();

    /** List of tenant configurations. Each tenant defines which specs to diffuse. */
    List<TenantConfig> tenants();

    interface ServerConfig {
        /** HTTP server port for health and metrics endpoints. */
        @WithDefault("8080")
        int port();
    }

    /**
     * Per-tenant configuration. Each optional list, when present, enables diffusion
     * for the named specs of that type. When absent, that streamlet type is disabled
     * for the tenant.
     */
    interface TenantConfig {
        String name();
        Optional<List<String>> wfSpecs();
        Optional<List<String>> userTaskDefs();
        Optional<List<String>> externalEventDefs();
        Optional<List<String>> workflowEventDefs();
    }
}
