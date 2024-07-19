package io.littlehorse.usertasks.idp_adaptors.keycloak;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "keycloak-properties")
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Data
public class KeycloakInstanceProperties {
    private String url;
    private String clientId;
}
