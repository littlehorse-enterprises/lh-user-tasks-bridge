package io.littlehorse.usertasks.configurations;

import com.c4_soft.springaddons.security.oidc.starter.OpenidProviderPropertiesResolver;
import com.c4_soft.springaddons.security.oidc.starter.properties.OpenidProviderProperties;
import com.c4_soft.springaddons.security.oidc.starter.properties.SpringAddonsOidcProperties;
import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManagerResolver;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.net.URI;
import java.util.Map;
import java.util.Optional;

@Configuration
@EnableMethodSecurity
@EnableWebSecurity
/*
  This configuration is using default auto config for the filterChain. No CSRF protection yet. No session.
 */
public class WebSecurityConfiguration {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http, AuthenticationManagerResolver<HttpServletRequest> authenticationManagerResolver)
            throws Exception {
        http.authorizeHttpRequests(
                        auth -> auth
                                .requestMatchers(HttpMethod.GET, "/v3/api-docs.yaml")// This will be used when open-api spec is in place
                                .permitAll()
                                .anyRequest()
                                .authenticated())
                .oauth2ResourceServer(oauth2 -> oauth2.authenticationManagerResolver(authenticationManagerResolver))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .cors(Customizer.withDefaults())
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    @Component
    public static class IssuerStartsWithOpenidProviderPropertiesResolver implements OpenidProviderPropertiesResolver {
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

    @Bean
    public LittleHorseGrpc.LittleHorseBlockingStub lhClient() {
        return new LHConfig().getBlockingStub();
    }
}
