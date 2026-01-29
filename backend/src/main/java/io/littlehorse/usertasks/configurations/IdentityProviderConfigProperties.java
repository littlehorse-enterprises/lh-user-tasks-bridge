package io.littlehorse.usertasks.configurations;

import static io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties.getCustomIdentityProviderProperties;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.IdentityProviderVendor;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import jakarta.annotation.Nullable;
import java.util.List;
import lombok.Data;
import lombok.NonNull;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

@Data
@AutoConfiguration
@ConfigurationProperties(prefix = "com.c4-soft.springaddons.oidc")
public class IdentityProviderConfigProperties {
    private List<CustomIdentityProviderProperties> ops = List.of();

    @Nullable
    public IStandardIdentityProviderAdapter getIdentityProviderHandler(
            @NonNull final String accessToken, boolean strict) throws JsonProcessingException {
        final CustomIdentityProviderProperties customIdentityProviderProperties =
                getCustomIdentityProviderProperties(accessToken, this);

        if (customIdentityProviderProperties.getVendor() == IdentityProviderVendor.KEYCLOAK) {
            return new KeycloakAdapter();
        } else {
            if (strict) {
                throw new ResponseStatusException(HttpStatus.NOT_ACCEPTABLE);
            } else {
                return null;
            }
        }
    }
}
