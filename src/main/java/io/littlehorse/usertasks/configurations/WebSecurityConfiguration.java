package io.littlehorse.usertasks.configurations;

import com.c4_soft.springaddons.security.oidc.starter.OpenidProviderPropertiesResolver;
import com.c4_soft.springaddons.security.oidc.starter.properties.OpenidProviderProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import lombok.Data;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableMethodSecurity
/*
  This configuration is using default auto config for the filterChain. No CSRF protection yet. No session.
 */
public class WebSecurityConfiguration {

    @Component
    @Data
    public class IssuerStartsWithOpenidProviderPropertiesResolver implements OpenidProviderPropertiesResolver {
        private final SpringAddonsOidcProperties properties;

        public IssuerStartsWithOpenidProviderPropertiesResolver(SpringAddonsOidcProperties properties) {
            this.properties = properties;
        }

        @Override
        public Optional<OpenidProviderProperties> resolve(Map<String, Object> claimSet) {
            final var tokenIss = Optional.ofNullable(claimSet.get(JwtClaimNames.ISS)).map(Object::toString)
                    .orElseThrow(() -> new RuntimeException("Invalid token: missing issuer"));
            return properties.getOps().stream().filter(opProps -> {
                final var opBaseHref = Optional.ofNullable(opProps.getIss()).map(URI::toString).orElse(null);
                if (!StringUtils.hasText(opBaseHref)) {
                    return false;
                }
                return tokenIss.startsWith(opBaseHref);
            }).findAny();
        }
    }
}
