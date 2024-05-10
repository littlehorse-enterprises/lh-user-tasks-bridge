package io.littlehorse.usertasks.properties;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.lang.NonNull;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@ConfigurationProperties(prefix = "tenants")
public record TenantOIDCProperties(Map<String, String> oidcProvidersConfig) {
    public Set<String> getConfiguredTenants() {
        return this.oidcProvidersConfig().keySet().stream()
                .map(key -> {
                    String[] splitKey = key.split("\\.");
                    return splitKey.length > 0 ? splitKey[0] : "";
                })
                .collect(Collectors.toSet());
    }

    public Map<String, String> getOIDCProviderConfigurationForTenant(@NonNull String tenantId) {
        return this.oidcProvidersConfig().entrySet().stream()
                .filter(entry -> entry.getKey().startsWith(tenantId))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }
}
