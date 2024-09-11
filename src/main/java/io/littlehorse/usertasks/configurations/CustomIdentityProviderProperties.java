package io.littlehorse.usertasks.configurations;

import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Optional;
import java.util.Set;
import java.util.function.Predicate;

@Data
@AllArgsConstructor
public class CustomIdentityProviderProperties {
    private URI iss;
    private String usernameClaim;
    private IdentityProviderVendor vendor;
    private String tenantId;
    private Set<String> clients;

    public static CustomIdentityProviderProperties getCustomIdentityProviderProperties(@NonNull String issuerUrl,
                                                                                       @NonNull String tenantId,
                                                                                       @NonNull String clientId,
                                                                                       @NonNull IdentityProviderConfigProperties identityProviderConfigProperties) {
        Optional<CustomIdentityProviderProperties> actualProperties = identityProviderConfigProperties.getOps().stream()
                .filter(matchingConfigurations(issuerUrl, tenantId, clientId))
                .findFirst();

        return actualProperties.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }

    private static Predicate<CustomIdentityProviderProperties> matchingConfigurations(String issuerUrl, String tenantId,
                                                                                      String clientId) {
        return customProperties -> StringUtils.equalsIgnoreCase(customProperties.getIss().toString(), issuerUrl)
                && StringUtils.equalsIgnoreCase(customProperties.getTenantId(), tenantId)
                && !CollectionUtils.isEmpty(customProperties.getClients()) && customProperties.getClients().contains(clientId);
    }
}
