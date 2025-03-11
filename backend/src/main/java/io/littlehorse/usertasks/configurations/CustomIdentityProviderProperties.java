package io.littlehorse.usertasks.configurations;

import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.util.TokenUtil;
import io.littlehorse.usertasks.util.enums.CustomUserIdClaim;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;

import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ALLOWED_TOKEN_CUSTOM_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ISSUER_URL_CLAIM;

@Data
@AllArgsConstructor
public class CustomIdentityProviderProperties {
    private URI iss;
    private String usernameClaim;
    private CustomUserIdClaim userIdClaim;
    private IdentityProviderVendor vendor;
    private String labelName;
    private String tenantId;
    private Set<String> clients;
    private String clientIdClaim;
    private List<SpringAddonsOidcProperties.OpenidProviderProperties.SimpleAuthoritiesMappingProperties> authorities;

    public static CustomIdentityProviderProperties getCustomIdentityProviderProperties(@NonNull String accessToken,
                                                                                       @NonNull IdentityProviderConfigProperties identityProviderConfigProperties) throws JsonProcessingException {
        Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
        var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
        var tenantId = (String) tokenClaims.get(ALLOWED_TOKEN_CUSTOM_CLAIM);

        CustomIdentityProviderProperties foundIdPProperties = identityProviderConfigProperties.getOps().stream()
                .filter(matchesIssuerAndTenantPredicate(issuerUrl, tenantId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));

        String clientIdClaim = foundIdPProperties.getClientIdClaim();
        var clientId = (String) tokenClaims.get(clientIdClaim);

        boolean hasValidIdPConfiguration = !CollectionUtils.isEmpty(foundIdPProperties.getClients()) && foundIdPProperties.getClients().contains(clientId);

        if (!hasValidIdPConfiguration) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED);
        }

        return foundIdPProperties;
    }

    private static Predicate<CustomIdentityProviderProperties> matchesIssuerAndTenantPredicate(String issuerUrl, String tenantId) {
        return customProperties -> StringUtils.equalsIgnoreCase(issuerUrl, customProperties.getIss().toString())
                && StringUtils.equalsIgnoreCase(tenantId, customProperties.getTenantId());
    }
}
