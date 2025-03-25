package io.littlehorse.usertasks.models.responses;

import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code IdentityProviderDTO} is a Data Transfer Object that contains information about a specific Identity Provider's
 * configuration on a per-clientId basis.
 */
@Builder
@AllArgsConstructor
@Data
public class IdentityProviderDTO {
    @NotNull
    private IdentityProviderVendor vendor;
    @NotNull
    private String labelName;
    @NotNull
    private String issuer;
    @NotNull
    private String clientId;
    @NotEmpty
    private Set<String> authorities;

    /**
     Transforms a {@code CustomIdentityProviderProperties} object into a clientId-based {@code Set} of {@code IdentityProviderDTO}
     containing configuration properties inherent to an Identity Provider.

     @return A {@link  java.util.Set} of type {@link  io.littlehorse.usertasks.models.responses.IdentityProviderDTO}
     @see io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties
     */
    public static Set<IdentityProviderDTO> fromConfigProperties(CustomIdentityProviderProperties configProperties) {
        Set<String> authorities = mapAuthorities(configProperties);
        Set<IdentityProviderDTO> providers = new HashSet<>();

        configProperties.getClients().forEach(clientId -> providers.add(IdentityProviderDTO.builder()
                .labelName(configProperties.getLabelName())
                .vendor(configProperties.getVendor())
                .issuer(configProperties.getIss().toString())
                .clientId(clientId)
                .authorities(authorities)
                .build()));

        return Collections.unmodifiableSet(providers);
    }

    private static Set<String> mapAuthorities(CustomIdentityProviderProperties configProperties) {
        return configProperties.getAuthorities().stream()
                .map(SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties::getPath)
                .collect(Collectors.toUnmodifiableSet());
    }
}
