package io.littlehorse.usertasks.idp_adaptors.keycloak;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class KeycloakBeans {
    @Bean
    public KeycloakInstanceProperties keycloakProperties() {
        return new KeycloakInstanceProperties();
    }
}
