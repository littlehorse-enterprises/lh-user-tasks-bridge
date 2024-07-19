package io.littlehorse.usertasks.idp_adaptors.keycloak;

import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KeycloakAdminService {
    private final KeycloakInstanceProperties keycloakProperties;

    public KeycloakAdminService(KeycloakInstanceProperties keycloakProperties) {
        this.keycloakProperties = keycloakProperties;
    }

    public Set<String> getUserGroupsByRealm(String realm, String accessToken) {
        Keycloak keycloak = getKeycloakInstance(realm, accessToken);

        return keycloak.realm(realm).groups().groups().stream()
                .map(GroupRepresentation::getName)
                .collect(Collectors.toSet());
    }

    public Set<String> getUsersByRealm(String realm, String accessToken) {
        Keycloak keycloak = getKeycloakInstance(realm, accessToken);

        return keycloak.realm(realm).users().list().stream()
                .map(UserRepresentation::getUsername)
                .collect(Collectors.toSet());
    }

    private Keycloak getKeycloakInstance(String realm, String accessToken) {
        return Keycloak.getInstance(keycloakProperties.getUrl(), realm, keycloakProperties.getClientId(), accessToken);
    }
}
