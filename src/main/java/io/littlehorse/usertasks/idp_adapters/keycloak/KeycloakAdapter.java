package io.littlehorse.usertasks.idp_adapters.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.util.TokenUtil;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class KeycloakAdapter implements IStandardIdentityProviderAdapter {

    @Override
    public Set<String> getUserGroups(Map<String, Object> params) {
        try {
            var realm = (String) params.get("realm");
            var accessToken = (String) params.get("accessToken");

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);

            return keycloak.realm(realm).groups().groups().stream()
                    .map(GroupRepresentation::getName)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Override
    public Set<String> getUsers(Map<String, Object> params) {
        try {
            var realm = (String) params.get("realm");
            var accessToken = (String) params.get("accessToken");

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);

            return keycloak.realm(realm).users().list().stream()
                    .map(UserRepresentation::getUsername)
                    .collect(Collectors.toSet());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private Keycloak getKeycloakInstance(String realm, String accessToken) throws JsonProcessingException {
        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get("iss");
            var keycloakBaseUrl = issuerUrl.split("/realms/")[0];
            var clientId = (String) tokenClaims.get("azp");

            return Keycloak.getInstance(keycloakBaseUrl, realm, clientId, accessToken);
        } catch (JsonProcessingException e) {
            throw new RuntimeException(e);
        }
    }
}
