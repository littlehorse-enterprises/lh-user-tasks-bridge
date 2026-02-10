package io.littlehorse.streamsense.diffuser.config;

import io.smallrye.config.ConfigMapping;
import io.smallrye.config.WithDefault;

import java.util.List;
import java.util.Optional;

@ConfigMapping(prefix = "app")
public interface AppConfig {

    String lhClusterId();

    ServerConfig server();

    List<TenantConfig> tenants();

    interface ServerConfig {
        @WithDefault("8080")
        int port();
    }

    interface TenantConfig {
        String name();
        Optional<List<String>> wfSpecs();
        Optional<List<String>> userTaskDefs();
        Optional<List<String>> externalEventDefs();
        Optional<List<String>> workflowEventDefs();
    }
}
