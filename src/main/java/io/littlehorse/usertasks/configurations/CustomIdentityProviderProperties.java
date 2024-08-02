package io.littlehorse.usertasks.configurations;

import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.net.URI;
import java.util.Optional;

@Data
@AllArgsConstructor
public class CustomIdentityProviderProperties {
    private URI iss;
    private String usernameClaim;
    private IdentityProviderVendor vendor;

    public static CustomIdentityProviderProperties getCustomIdentityProviderProperties(@NonNull String issuerUrl,
                                                                                       @NonNull IdentityProviderConfigProperties identityProviderConfigProperties) {
        Optional<CustomIdentityProviderProperties> actualProperties = identityProviderConfigProperties.getOps().stream()
                .filter(customProperties ->
                        org.apache.commons.lang3.StringUtils.equalsIgnoreCase(customProperties.getIss().toString(), issuerUrl))
                .findFirst();

        return actualProperties.orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED));
    }
}
