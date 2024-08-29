package io.littlehorse.usertasks.idp_adapters.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;
import io.littlehorse.usertasks.util.TokenUtil;
import jakarta.ws.rs.NotFoundException;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
    public static final String USER_ID_MAP_KEY = "userId";

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
            //TODO: It's probably worth it to remove the "realm" from the params map and take it from the accessToken instead,
            // since users cannot see anything from outside the realm that they belong to.
            var realm = (String) params.get(REALM_MAP_KEY);
            var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
            var requestedUserId = (String) params.get(USER_ID_MAP_KEY);

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);

            var userId = StringUtils.isNotBlank(requestedUserId)
                    ? requestedUserId
                    : (String) TokenUtil.getTokenClaims(accessToken).get(USER_ID_CLAIM);

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
            var email = (String) params.get("email");
            var firstName = (String) params.get("firstName");
            var lastName = (String) params.get("lastName");
            var username = (String) params.get("username");
            var userGroup = (String) params.get("userGroup");
            var firstResult = (Integer) params.get("firstResult");
            var maxResults = (Integer) params.get("maxResults");

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);
            RealmResource realmResource = keycloak.realm(realm);

            Set<UserRepresentation> foundUsers = filterUsers(realmResource, email, firstName, lastName, username, userGroup,
                    firstResult, maxResults);

            Set<UserDTO> setOfUsers = foundUsers.stream()
                    .map(UserDTO.transform())
                    .collect(Collectors.toSet());

            return new UserListDTO(setOfUsers);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw e;
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
            var userId = (String) params.get(USER_ID_MAP_KEY);
            var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);

            String realm = getRealmFromToken(accessToken);

            Keycloak keycloak = getKeycloakInstance(realm, accessToken);
            UserRepresentation userRepresentation = keycloak.realm(realm).users().get(userId).toRepresentation();

            return UserDTO.transform().apply(userRepresentation);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (NotFoundException e) {
            log.error("User could not be found", e);
            return null;
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching User's info from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void validateAssignmentProperties(Map<String, Object> params) {
        log.info("Validating assignment properties!");

        var userId = (String) params.get(USER_ID_MAP_KEY);
        var userGroup = (String) params.get("userGroup");
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);

        if (CollectionUtils.isEmpty(params) || (StringUtils.isBlank(userId) && StringUtils.isBlank(userGroup))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid arguments were received to complete reassignment.");
        }

        if (StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(userGroup)) {
            validateUserInfoForAssignment(params);
            validateUserGroupForAssignment(params, accessToken, userGroup);
        } else if (StringUtils.isNotBlank(userId)) {
            validateUserInfoForAssignment(params);
        } else {
            validateUserGroupForAssignment(params, accessToken, userGroup);
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

    private void validateUserInfoForAssignment(Map<String, Object> params) {
        UserDTO userInfo = getUserInfo(params);

        if (!Objects.nonNull(userInfo)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign Task to non-existent user.");
        }
    }

    private void validateUserGroupForAssignment(Map<String, Object> params, String accessToken, String userGroup) {
        var userId = (String) params.get(USER_ID_MAP_KEY);
        String realm = getRealmFromToken(accessToken);
        Map<String, Object> paramsWithRealm = new HashMap<>();
        paramsWithRealm.put(REALM_MAP_KEY, realm);
        paramsWithRealm.putAll(params);

        Set<String> userGroups = StringUtils.isNotBlank(userId)
                ? getMyUserGroups(paramsWithRealm)
                : getUserGroups(paramsWithRealm);

        if (CollectionUtils.isEmpty(userGroups) || !userGroups.contains(userGroup)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign Task to non-existent group, " +
                    "nor can the Task be assigned to an existing group that the requested user is not a member of.");
        }
    }

    private Set<UserRepresentation> filterUsers(RealmResource realmResource, String email, String firstName, String lastName,
                                                String username, String userGroup, int firstResult, int maxResults) {
        Set<UserRepresentation> filteredUsers;
        UsersResource usersResource = realmResource.users();

        if (StringUtils.isNotBlank(username) || StringUtils.isNotBlank(firstName) || StringUtils.isNotBlank(lastName)
                || StringUtils.isNotBlank(email)) {
            List<UserRepresentation> searchResult = usersResource.search(username, firstName, lastName, email, firstResult,
                    maxResults, true, false, false);
            filteredUsers = new HashSet<>(searchResult);
        } else if (StringUtils.isNotBlank(userGroup)) {
            //TODO: Change response of endpoint to fetch list of groups. Now I need to return an array of DTOs with id and name
            // so that I can use the id of the groups to search users by group
            filteredUsers = new HashSet<>(realmResource.groups().group(userGroup).members(firstResult, maxResults));
        } else {
            filteredUsers = new HashSet<>(usersResource.list(firstResult, maxResults));
        }

        return filteredUsers;
    }
}
