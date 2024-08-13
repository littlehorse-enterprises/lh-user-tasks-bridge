package io.littlehorse.usertasks.idp_adapters.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;
import io.littlehorse.usertasks.util.TokenUtil;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ISSUER_URL_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.USER_ID_CLAIM;

@Service
@Slf4j
public class KeycloakAdapter implements IStandardIdentityProviderAdapter {
    public static final String REALM_URL_PATH = "/realms/";
    public static final String REALM_MAP_KEY = "realm";
    public static final String ACCESS_TOKEN_MAP_KEY = "accessToken";

    @Override
    public Set<String> getUserGroups(Map<String, Object> params) {
        try {
            var realm = (String) params.get(REALM_MAP_KEY);
            var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);

            return keycloak.realm(realm).groups().groups().stream()
                    .map(GroupRepresentation::getName)
                    .collect(Collectors.toSet());
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching all Groups from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public Set<String> getMyUserGroups(Map<String, Object> params) {
        try {
            var realm = (String) params.get(REALM_MAP_KEY);
            var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);

            var userId = (String) TokenUtil.getTokenClaims(accessToken).get(USER_ID_CLAIM);

            return keycloak.realm(realm).users().get(userId).groups().stream()
                    .map(GroupRepresentation::getName)
                    .collect(Collectors.toSet());
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching all My Groups from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public UserListDTO getUsers(Map<String, Object> params) {
        try {
            var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
            var realm = getRealmFromToken(accessToken);

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);

            Set<UserDTO> setOfUsers = keycloak.realm(realm).users().list().stream()
                    .map(userRepresentation -> UserDTO.builder()
                            .id(userRepresentation.getId())
                            .email(userRepresentation.getEmail())
                            .username(userRepresentation.getUsername())
                            .build())
                    .collect(Collectors.toSet());

            return new UserListDTO(setOfUsers);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching all Users from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void validateUserGroup(String userGroup, String accessToken) {
        String realm = getRealmFromToken(accessToken);
        Map<String, Object> params = Map.of(REALM_MAP_KEY, realm, ACCESS_TOKEN_MAP_KEY, accessToken);
        Set<String> myUserGroups = getMyUserGroups(params);

        if (CollectionUtils.isEmpty(myUserGroups) || !myUserGroups.contains(userGroup)) {
            log.error("Cannot access requested group.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public UserDTO getUserInfo(Map<String, Object> params) {
        try {
            var userId = (String) params.get("userId");
            var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);

            String realm = getRealmFromToken(accessToken);

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);
            UserRepresentation userRepresentation = keycloak.realm(realm).users().get(userId).toRepresentation();

            return UserDTO.builder()
                    .id(userRepresentation.getId())
                    .email(userRepresentation.getEmail())
                    .username(userRepresentation.getUsername())
                    .build();
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching User's info from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    private Keycloak getKeycloakInstance(String realm, String accessToken) {
        try {
            Map<String, Object> tokenClaims = TokenUtil.getTokenClaims(accessToken);
            var issuerUrl = (String) tokenClaims.get(ISSUER_URL_CLAIM);
            var keycloakBaseUrl = issuerUrl.split(REALM_URL_PATH)[0];
            var clientId = (String) tokenClaims.get("azp");

            return Keycloak.getInstance(keycloakBaseUrl, realm, clientId, accessToken);
        } catch (JsonProcessingException e) {
            var errorMessage = "Something went wrong while reading claims.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        } catch (Exception e) {
            var errorMessage = "Something went wrong while creating Keycloak instance.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    private String getRealmFromToken(@NonNull String accessToken) {
        try {
            String issuerUrl = (String) TokenUtil.getTokenClaims(accessToken).get(ISSUER_URL_CLAIM);

            return issuerUrl.split(REALM_URL_PATH)[1];
        } catch (JsonProcessingException e) {
            var errorMessage = "Something went wrong while reading claims.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        } catch (Exception e) {
            var errorMessage = "Something went wrong while getting realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }
}
