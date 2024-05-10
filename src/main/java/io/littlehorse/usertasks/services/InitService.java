package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.properties.TenantOIDCProperties;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Set;

@Service
@Slf4j
public class InitService {

    //TODO: This will be changed and TenantOIDCProperties will probably be removed, for there might be another way of
    // validating if a tenant is valid from properties file
    @Autowired
    private TenantOIDCProperties allTenantsOIDCProperties;

    public boolean isValidTenant(String tenantId) {
        Set<String> configuredTenants = allTenantsOIDCProperties.getConfiguredTenants();

        if (configuredTenants.isEmpty()) {
            throw new RuntimeException("There are no tenants configured");
        }

        return configuredTenants.contains(tenantId.trim());
    }
}
