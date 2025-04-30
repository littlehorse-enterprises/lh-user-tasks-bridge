package io.littlehorse.usertasks.idp_adapters.keycloak;

import com.fasterxml.jackson.core.JsonProcessingException;
import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateManagedUserRequest;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.responses.*;
import io.littlehorse.usertasks.util.TokenUtil;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.*;
import org.keycloak.representations.idm.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

import java.util.*;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

import static io.littlehorse.usertasks.util.constants.AuthoritiesConstants.LH_USER_TASKS_ADMIN_ROLE;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.ISSUER_URL_CLAIM;
import static io.littlehorse.usertasks.util.constants.TokenClaimConstants.USER_ID_CLAIM;

@Service
@Slf4j
public class KeycloakAdapter implements IStandardIdentityProviderAdapter {
    public static final String REALM_URL_PATH = "/realms/";
    public static final String REALM_MAP_KEY = "realm";
    public static final String ACCESS_TOKEN_MAP_KEY = "accessToken";
    public static final String USER_ID_MAP_KEY = "userId";
    public static final String USER_GROUP_ID_MAP_KEY = "userGroupId";
    public static final String EMAIL_MAP_KEY = "email";
    public static final String FIRST_NAME_MAP_KEY = "firstName";
    public static final String LAST_NAME_MAP_KEY = "lastName";
    public static final String USERNAME_MAP_KEY = "username";
    public static final String USER_GROUP_NAME_MAP_KEY = "userGroupName";
    public static final String FIRST_RESULT_MAP_KEY = "firstResult";
    public static final String MAX_RESULTS_MAP_KEY = "maxResults";
    public static final String VIEW_USERS_ROLE_NAME = "view-users";
    public static final String VIEW_REALM_ROLE_NAME = "view-realm";
    public static final String VIEW_CLIENTS_ROLE_NAME = "view-clients";
    public static final String MANAGE_USERS_ROLE_NAME = "manage-users";
    public static final String REALM_MANAGEMENT_CLIENT_ID = "realm-management";

    @Override
    public UserGroupListDTO getUserGroups(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            Set<UserGroupDTO> foundUserGroups = keycloak.realm(realm).groups().groups().stream()
                    .map(groupRepresentation -> UserGroupDTO.transform().apply(groupRepresentation))
                    .collect(Collectors.toSet());

            return !CollectionUtils.isEmpty(foundUserGroups)
                    ? new UserGroupListDTO(foundUserGroups)
                    : new UserGroupListDTO(Set.of());
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
    public UserGroupListDTO getMyUserGroups(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            var requestedUserId = (String) params.get(USER_ID_MAP_KEY);

            var userId = StringUtils.isNotBlank(requestedUserId)
                    ? requestedUserId
                    : (String) TokenUtil.getTokenClaims(accessToken).get(USER_ID_CLAIM);

            Set<UserGroupDTO> foundUserGroups = keycloak.realm(realm).users().get(userId).groups().stream()
                    .map(groupRepresentation -> UserGroupDTO.transform().apply(groupRepresentation))
                    .collect(Collectors.toSet());

            return !CollectionUtils.isEmpty(foundUserGroups)
                    ? new UserGroupListDTO(foundUserGroups)
                    : new UserGroupListDTO(Set.of());
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
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)){
            var email = (String) params.get("email");
            var firstName = (String) params.get("firstName");
            var lastName = (String) params.get("lastName");
            var username = (String) params.get("username");
            var userGroupId = (String) params.get(USER_GROUP_ID_MAP_KEY);
            var firstResult = (Integer) params.get(FIRST_RESULT_MAP_KEY);
            var maxResults = (Integer) params.get(MAX_RESULTS_MAP_KEY);

            RealmResource realmResource = keycloak.realm(realm);

            Set<UserRepresentation> foundUsers = filterUsers(realmResource, email, firstName, lastName, username, userGroupId,
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
    public IDPUserListDTO getManagedUsers(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            var email = (String) params.get("email");
            var firstName = (String) params.get("firstName");
            var lastName = (String) params.get("lastName");
            var username = (String) params.get("username");
            var userGroupId = (String) params.get(USER_GROUP_ID_MAP_KEY);
            var firstResult = (Integer) params.get(FIRST_RESULT_MAP_KEY);
            var maxResults = (Integer) params.get(MAX_RESULTS_MAP_KEY);

            RealmResource realmResource = keycloak.realm(realm);

            Set<UserRepresentation> foundUsers = filterUsers(realmResource, email, firstName, lastName, username, userGroupId,
                    firstResult, maxResults);

            Set<IDPUserDTO> setOfUsers = foundUsers.stream()
                    .filter(Objects::nonNull)
                    .map(buildUserDTO(realmResource))
                    .collect(Collectors.toUnmodifiableSet());

            return new IDPUserListDTO(setOfUsers);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw e;
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching all managed Users from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public IDPUserDTO getManagedUser(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            var userId = (String) params.get(USER_ID_MAP_KEY);

            RealmResource realmResource = keycloak.realm(realm);
            UserRepresentation userRepresentation = realmResource.users().get(userId).toRepresentation();

            return Optional.ofNullable(userRepresentation)
                    .map(buildUserDTO(realmResource))
                    .orElse(null);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw e;
        } catch (NotFoundException e) {
            log.warn("User data could not be found");
            return null;
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching managed User from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void updateManagedUser(Map<String, Object> params) {
        log.info("Starting User update!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var userId = (String) params.get(USER_ID_MAP_KEY);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            UserRepresentation userRepresentation = buildBasicUserRepresentationForUpdate(params);

            keycloak.realm(realm).users().get(userId).update(userRepresentation);

            log.info("User successfully updated in realm {}!", realm);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while creating a User in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void deleteManagedUser(Map<String, Object> params) {
        log.info("Starting User deletion!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var userId = (String) params.get(USER_ID_MAP_KEY);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            Response response = keycloak.realm(realm).users().delete(userId);

            if (response.getStatus() != HttpStatus.NO_CONTENT.value()) {
                throw new AdapterException("User deletion failed in Keycloak!");
            }

            log.info("User successfully deleted from realm {}!", realm);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while deleting a User from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void validateUserGroup(String userGroupId, String accessToken) {
        String realm = getRealmFromToken(accessToken);
        Map<String, Object> params = Map.of(REALM_MAP_KEY, realm, ACCESS_TOKEN_MAP_KEY, accessToken);
        UserGroupListDTO myUserGroups = getMyUserGroups(params);

        if (CollectionUtils.isEmpty(myUserGroups.getGroups())
                || myUserGroups.getGroups().stream().noneMatch(equalUserGroupIdPredicate(userGroupId))) {
            log.error("Cannot access requested group.");
            throw new ResponseStatusException(HttpStatus.FORBIDDEN);
        }
    }

    @Override
    public UserDTO getUserInfo(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            var userId = (String) params.get(USER_ID_MAP_KEY);
            var email = (String) params.get("email");
            var username = (String) params.get("username");

            UserRepresentation userRepresentation = null;

            if (StringUtils.isNotBlank(email)) {
                List<UserRepresentation> userRepresentations = keycloak.realm(realm).users().searchByEmail(email, true);

                if (!CollectionUtils.isEmpty(userRepresentations)) {
                    userRepresentation = userRepresentations.getFirst();
                }
            } else if (StringUtils.isNotBlank(username)) {
                List<UserRepresentation> userRepresentations = keycloak.realm(realm).users().searchByUsername(username, true);

                if (!CollectionUtils.isEmpty(userRepresentations)) {
                    userRepresentation = userRepresentations.getFirst();
                }
            } else {
                userRepresentation = keycloak.realm(realm).users().get(userId).toRepresentation();
            }

            return Optional.ofNullable(userRepresentation)
                    .map(representation -> UserDTO.transform().apply(representation))
                    .orElse(null);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (NotFoundException e) {
            log.warn("User could not be found");
            return null;
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching User's info from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public UserGroupDTO getUserGroup(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            var userGroupId = (String) params.get(USER_GROUP_ID_MAP_KEY);
            var userGroupName = (String) params.get(USER_GROUP_NAME_MAP_KEY);

            GroupRepresentation groupRepresentation = null;

            if (StringUtils.isNotBlank(userGroupName)) {
                List<GroupRepresentation> groupRepresentations = keycloak.realm(realm).groups().query(userGroupName);

                if (!CollectionUtils.isEmpty(groupRepresentations)) {
                    Set<GroupRepresentation> actualGroups = groupRepresentations.stream()
                            .filter(foundGroup -> StringUtils.equalsIgnoreCase(foundGroup.getName(), userGroupName))
                            .collect(Collectors.toSet());

                    groupRepresentation = !CollectionUtils.isEmpty(actualGroups)
                            ? actualGroups.iterator().next()
                            : null;
                }
            } else if (StringUtils.isNotBlank(userGroupId)){
                groupRepresentation = keycloak.realm(realm).groups().group(userGroupId).toRepresentation();
            }

            return Objects.nonNull(groupRepresentation)
                    ? UserGroupDTO.transform().apply(groupRepresentation)
                    : null;
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (NotFoundException e) {
            log.warn("Group could not be found");
            return null;
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching Group's info from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void validateAssignmentProperties(Map<String, Object> params) {
        log.info("Validating assignment properties!");

        var userId = (String) params.get(USER_ID_MAP_KEY);
        var userGroupId = (String) params.get(USER_GROUP_ID_MAP_KEY);
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);

        if (CollectionUtils.isEmpty(params) || (StringUtils.isBlank(userId) && StringUtils.isBlank(userGroupId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No valid arguments were received to complete reassignment.");
        }

        if (StringUtils.isNotBlank(userId) && StringUtils.isNotBlank(userGroupId)) {
            validateUserInfoForAssignment(params);
            validateUserGroupForAssignment(params, accessToken, userGroupId);
        } else if (StringUtils.isNotBlank(userId)) {
            validateUserInfoForAssignment(params);
        } else {
            validateUserGroupForAssignment(params, accessToken, userGroupId);
        }
    }

    @Override
    public void createManagedUser(Map<String, Object> params) {
        log.debug("Starting User creation!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            UserRepresentation userRepresentation = buildBasicUserRepresentationForCreation(params);

            RealmResource realmResource = keycloak.realm(realm);
            UsersResource usersResource = realmResource.users();

            try (Response response = usersResource.create(userRepresentation)) {
                if (response.getStatusInfo().getStatusCode() != 201) {
                    String exceptionMessage = String.format("User creation failed within realm %s with status: %s!", realm,
                            response.getStatusInfo().getStatusCode());

                    throw new AdapterException(exceptionMessage);
                }
            }

            addViewUsersRole(realmResource, usersResource, userRepresentation.getUsername());

            log.debug("User successfully created within realm {}!", realm);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while creating a User in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void setPassword(String userId, Map<String, Object> params) {
        log.info("Starting to set user's password!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var password = (String) params.get("password");
        var isTemporary = (Boolean) params.get("isTemporary");

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            UserResource userResource = keycloak.realm(realm).users().get(userId);
            CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
            credentialRepresentation.setTemporary(isTemporary);
            credentialRepresentation.setValue(password);
            credentialRepresentation.setType(CredentialRepresentation.PASSWORD);

            if (CollectionUtils.isEmpty(userResource.credentials())) {
                UserRepresentation representation = userResource.toRepresentation();
                representation.setCredentials(Collections.singletonList(credentialRepresentation));
                userResource.update(representation);

                log.info("Password successfully set!");
            } else {
                userResource.resetPassword(credentialRepresentation);
                log.info("Password successfully reset!");
            }
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (NotFoundException e) {
            log.error(e.getMessage());
            throw new io.littlehorse.usertasks.exceptions.NotFoundException("User could not be found.");
        } catch (Exception e) {
            var errorMessage = "Something went wrong while setting a User's password in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void assignAdminRole(Map<String, Object> params) {
        log.debug("Starting to assign Admin roles!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var userId = (String) params.get(USER_ID_MAP_KEY);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            RealmResource realmResource = keycloak.realm(realm);

            RoleRepresentation adminRoleRepresentation = realmResource.roles().get(LH_USER_TASKS_ADMIN_ROLE).toRepresentation();
            UserResource userResource = realmResource.users().get(userId);

            userResource.roles().realmLevel().add(Collections.singletonList(adminRoleRepresentation));

            addAdditionalAdminRoles(realmResource, userResource);

            log.debug("Admin roles successfully added!");
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while assigning admin role to user in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void removeAdminRole(Map<String, Object> params) {
        log.info("Starting to remove Admin role!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var userId = (String) params.get(USER_ID_MAP_KEY);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            RealmResource realmResource = keycloak.realm(realm);

            RoleRepresentation adminRoleRepresentation = realmResource.roles().get(LH_USER_TASKS_ADMIN_ROLE).toRepresentation();

            realmResource.users().get(userId).roles().realmLevel().remove(Collections.singletonList(adminRoleRepresentation));

            log.info("Admin role successfully removed!");
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while removing admin role from user in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void joinGroup(Map<String, Object> params) {
        log.info("Starting to join a group!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var userId = (String) params.get(USER_ID_MAP_KEY);
        var groupId = (String) params.get(USER_GROUP_ID_MAP_KEY);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            keycloak.realm(realm).users().get(userId).joinGroup(groupId);

            log.info("User successfully joined a group!");
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (NotFoundException e) {
            log.error(e.getMessage());
            throw new io.littlehorse.usertasks.exceptions.NotFoundException("User or Group could not be found.");
        } catch (Exception e) {
            var errorMessage = "Something went wrong while joining user to a group in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void removeUserFromGroup(Map<String, Object> params) {
        log.info("Starting to remove user as group member!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var realm = getRealmFromToken(accessToken);
        var userId = (String) params.get(USER_ID_MAP_KEY);
        var groupId = (String) params.get(USER_GROUP_ID_MAP_KEY);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            keycloak.realm(realm).users().get(userId).leaveGroup(groupId);

            log.info("User successfully removed from group!");
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (NotFoundException e) {
            log.error(e.getMessage());
            throw new io.littlehorse.usertasks.exceptions.NotFoundException("User or Group could not be found.");
        } catch (Exception e) {
            var errorMessage = "Something went wrong while removing user from group in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void createGroup(Map<String, Object> params) {
        log.info("Starting Group creation!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var groupName = (String) params.get(USER_GROUP_NAME_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            GroupRepresentation groupRepresentation = new GroupRepresentation();
            groupRepresentation.setName(groupName);

            try (Response response = keycloak.realm(realm).groups().add(groupRepresentation)) {
                if (response.getStatusInfo().getStatusCode() != 201) {
                    String exceptionMessage = String.format("User creation failed within realm %s with status: %s!", realm,
                            response.getStatusInfo().getStatusCode());

                    throw new AdapterException(exceptionMessage);
                }

                log.info("Group successfully created within realm {}!", realm);
            }
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while creating a Group in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public Set<IDPGroupDTO> getGroups(Map<String, Object> params) {
        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var groupName = (String) params.get(USER_GROUP_NAME_MAP_KEY);
        var firstResult = (Integer) params.get(FIRST_RESULT_MAP_KEY);
        var maxResults = (Integer) params.get(MAX_RESULTS_MAP_KEY);

        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            RealmResource realmResource = keycloak.realm(realm);
            GroupsResource groupsResource = realmResource.groups();
            List<GroupRepresentation> foundGroups;

            if (StringUtils.isNotBlank(groupName)) {
                foundGroups = groupsResource.groups(groupName, firstResult, maxResults);
            } else {
                foundGroups = groupsResource.groups(firstResult, maxResults);
            }

            if (!CollectionUtils.isEmpty(foundGroups)) {
                return foundGroups.stream()
                        .map(IDPGroupDTO::transform)
                        .collect(Collectors.toUnmodifiableSet());
            } else {
                return Collections.emptySet();
            }
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw e;
        } catch (Exception e) {
            var errorMessage = "Something went wrong while fetching Groups from Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void updateGroup(Map<String, Object> params) {
        log.info("Starting Group's renaming!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var groupId = (String) params.get(USER_GROUP_ID_MAP_KEY);
        var groupName = (String) params.get(USER_GROUP_NAME_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            GroupResource groupResource = keycloak.realm(realm).groups().group(groupId);
            GroupRepresentation groupRepresentation = groupResource.toRepresentation();
            groupRepresentation.setName(groupName);

            groupResource.update(groupRepresentation);

            log.info("Group successfully renamed within realm {}!", realm);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while renaming a Group in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    @Override
    public void deleteGroup(Map<String, Object> params) {
        log.debug("Starting Group's deletion!");

        var accessToken = (String) params.get(ACCESS_TOKEN_MAP_KEY);
        var groupId = (String) params.get(USER_GROUP_ID_MAP_KEY);
        var realm = getRealmFromToken(accessToken);

        try (Keycloak keycloak = getKeycloakInstance(realm, accessToken)) {
            keycloak.realm(realm).groups().group(groupId).remove();

            log.debug("Group successfully deleted within realm {}!", realm);
        } catch (AdapterException e) {
            log.error(e.getMessage());
            throw new AdapterException(e.getMessage());
        } catch (Exception e) {
            var errorMessage = "Something went wrong while deleting a Group in Keycloak realm.";
            log.error(errorMessage, e);
            throw new AdapterException(errorMessage);
        }
    }

    public static Map<String, Object> buildParamsForUsersSearch(String accessToken, IDPUserSearchRequestFilter requestFilter,
                                                                int firstResult, int maxResults) {
        Map<String, Object> params = new HashMap<>();

        if (StringUtils.isNotBlank(requestFilter.getEmail())) {
            params.put(EMAIL_MAP_KEY, requestFilter.getEmail());
        }

        if (StringUtils.isNotBlank(requestFilter.getFirstName())) {
            params.put(FIRST_NAME_MAP_KEY, requestFilter.getFirstName());
        }

        if (StringUtils.isNotBlank(requestFilter.getLastName())) {
            params.put(LAST_NAME_MAP_KEY, requestFilter.getLastName());
        }

        if (StringUtils.isNotBlank(requestFilter.getUsername())) {
            params.put(USERNAME_MAP_KEY, requestFilter.getUsername());
        }

        if (StringUtils.isNotBlank(requestFilter.getUserGroupId())) {
            params.put(USER_GROUP_ID_MAP_KEY, requestFilter.getUserGroupId());
        }

        params.put(ACCESS_TOKEN_MAP_KEY, accessToken);
        params.put(FIRST_RESULT_MAP_KEY, firstResult);
        params.put(MAX_RESULTS_MAP_KEY, maxResults);

        return params;
    }

    @NonNull
    public static Map<String, Object> buildParamsForUserCreation(@NonNull String accessToken, @NonNull CreateManagedUserRequest request) {
        Map<String, Object> params = new HashMap<>();
        String firstName = StringUtils.isNotBlank(request.getFirstName()) ? request.getFirstName().trim() : null;
        String lastName = StringUtils.isNotBlank(request.getLastName()) ? request.getLastName().trim() : null;
        String email = StringUtils.isNotBlank(request.getEmail()) ? request.getEmail().trim() : null;
        String username;

        if (StringUtils.isNotBlank(request.getUsername())) {
            username = request.getUsername().trim();
        } else {
            throw new AdapterException("Cannot create User without username!");
        }

        //Done like this and not with Map.of() because some values could be null
        params.put(ACCESS_TOKEN_MAP_KEY, accessToken);
        params.put(FIRST_NAME_MAP_KEY, firstName);
        params.put(LAST_NAME_MAP_KEY, lastName);
        params.put(USERNAME_MAP_KEY, username);
        params.put(EMAIL_MAP_KEY, email);

        params.entrySet()
                .removeIf(entry -> Objects.isNull(entry.getValue()));

        return params;
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

    private String getRealmFromToken(String accessToken) {
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

    private void validateUserGroupForAssignment(Map<String, Object> params, String accessToken, String userGroupId) {
        var userId = (String) params.get(USER_ID_MAP_KEY);
        String realm = getRealmFromToken(accessToken);
        Map<String, Object> paramsWithRealm = new HashMap<>();
        paramsWithRealm.put(REALM_MAP_KEY, realm);
        paramsWithRealm.putAll(params);

        UserGroupListDTO userGroups = StringUtils.isNotBlank(userId)
                ? getMyUserGroups(paramsWithRealm)
                : getUserGroups(paramsWithRealm);

        if (CollectionUtils.isEmpty(userGroups.getGroups())
                || userGroups.getGroups().stream().noneMatch(equalUserGroupIdPredicate(userGroupId))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Cannot assign Task to non-existent group, " +
                    "nor can the Task be assigned to an existing group that the requested user is not a member of.");
        }
    }

    private Set<UserRepresentation> filterUsers(RealmResource realmResource, String email, String firstName, String lastName,
                                                String username, String userGroupId, int firstResult, int maxResults) {
        boolean shouldSearchInUsersResource = StringUtils.isNotBlank(username) || StringUtils.isNotBlank(firstName)
                || StringUtils.isNotBlank(lastName) || StringUtils.isNotBlank(email);

        if (shouldSearchInUsersResource && StringUtils.isNotBlank(userGroupId)) {
            throw new AdapterException("Combination of userGroup + other filters (username/email/firstName/lastName) is not supported");
        }

        Set<UserRepresentation> filteredUsers;
        UsersResource usersResource = realmResource.users();


        if (shouldSearchInUsersResource) {
            List<UserRepresentation> searchResult = usersResource.search(username, firstName, lastName, email, firstResult,
                    maxResults, true, false, false);
            filteredUsers = new HashSet<>(searchResult);
        } else if (StringUtils.isNotBlank(userGroupId)) {
            filteredUsers = new HashSet<>(realmResource.groups().group(userGroupId).members(firstResult, maxResults));
        } else {
            filteredUsers = new HashSet<>(usersResource.list(firstResult, maxResults));
        }

        return filteredUsers;
    }

    private Predicate<UserGroupDTO> equalUserGroupIdPredicate(String userGroupId) {
        return userGroupDTO -> StringUtils.equals(userGroupId, userGroupDTO.getId());
    }

    private Function<UserRepresentation, IDPUserDTO> buildUserDTO(@NonNull RealmResource realmResource) {
        return foundUser -> {
            UsersResource usersResource = realmResource.users();
            List<GroupRepresentation> foundGroups = usersResource.get(foundUser.getId()).groups();

            IDPUserDTO actualUserDTO = IDPUserDTO.transform(foundUser, foundGroups);

            List<RoleRepresentation> realmRoles = getRealmRolesByUser(foundUser, usersResource);
            Map<String, ClientMappingsRepresentation> clientRolesMappingsRepresentation =
                    getClientRoleMappingsByUser(foundUser, usersResource);

            addRealmRolesToUser(realmRoles, actualUserDTO);
            addClientRolesToUser(clientRolesMappingsRepresentation, actualUserDTO);

            return actualUserDTO;
        };
    }

    private void addClientRolesToUser(Map<String, ClientMappingsRepresentation> clientRoles, IDPUserDTO actualUserDTO) {
        if (!CollectionUtils.isEmpty(clientRoles)) {
            Map<String, Set<String>> mappedClientRoles = getMappedClientRoles(clientRoles);

            actualUserDTO.setClientRoles(mappedClientRoles);
        } else {
            actualUserDTO.setClientRoles(Collections.emptyMap());
        }
    }

    private void addRealmRolesToUser(List<RoleRepresentation> realmRoles, IDPUserDTO actualUserDTO) {
        if (!CollectionUtils.isEmpty(realmRoles)) {
            Set<String> roleNames = realmRoles.stream()
                    .map(RoleRepresentation::getName)
                    .collect(Collectors.toSet());
            actualUserDTO.setRealmRoles(roleNames);
        } else {
            actualUserDTO.setRealmRoles(Collections.emptySet());
        }
    }

    private List<RoleRepresentation> getRealmRolesByUser(UserRepresentation foundUser, UsersResource usersResource) {
        RoleMappingResource roleMappingResource = usersResource.get(foundUser.getId()).roles();

        return Optional.ofNullable(roleMappingResource)
                .map(roleResource -> {
                    RoleScopeResource roleScopeResource = roleResource.realmLevel();

                    return Optional.ofNullable(roleScopeResource)
                            .map(RoleScopeResource::listAll)
                            .orElse(Collections.emptyList());
                })
                .orElse(Collections.emptyList());
    }

    private Map<String, ClientMappingsRepresentation> getClientRoleMappingsByUser(UserRepresentation foundUser,
                                                                                  UsersResource usersResource) {
        RoleMappingResource roleMappingResource = usersResource.get(foundUser.getId()).roles();

        return Optional.ofNullable(roleMappingResource)
                .map(roleResource -> {
                    MappingsRepresentation allMappings = roleResource.getAll();

                    return Optional.ofNullable(allMappings)
                            .map(MappingsRepresentation::getClientMappings)
                            .orElse(Collections.emptyMap());
                })
                .orElse(Collections.emptyMap());
    }

    private Map<String, Set<String>> getMappedClientRoles(Map<String, ClientMappingsRepresentation> clientRoles) {
        return clientRoles.entrySet().stream()
                .map(entry -> {
                    Set<String> roleNames = getRoleNamesFromClientMappingsRepresentation(entry);

                    return Map.entry(entry.getKey(), roleNames);
                }).collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    private Set<String> getRoleNamesFromClientMappingsRepresentation(Map.Entry<String, ClientMappingsRepresentation> entry) {
        return entry.getValue().getMappings().stream()
                .map(RoleRepresentation::getName)
                .collect(Collectors.toSet());
    }

    private UserRepresentation buildBasicUserRepresentationForCreation(Map<String, Object> params) {
        var firstName = (String) params.get(FIRST_NAME_MAP_KEY);
        var lastName = (String) params.get(LAST_NAME_MAP_KEY);
        var username = (String) params.get(USERNAME_MAP_KEY);
        var email = (String) params.get(EMAIL_MAP_KEY);

        UserRepresentation userRepresentation = new UserRepresentation();

        if (StringUtils.isNotBlank(firstName)) {
            userRepresentation.setFirstName(firstName.trim());
        }

        if (StringUtils.isNotBlank(lastName)) {
            userRepresentation.setLastName(lastName.trim());
        }

        if (StringUtils.isNotBlank(username)) {
            userRepresentation.setUsername(username.trim());
        } else {
            throw new AdapterException("Cannot create User without username!");
        }

        if (StringUtils.isNotBlank(email)) {
            userRepresentation.setEmail(email.trim());
        }

        userRepresentation.setEmailVerified(true);
        userRepresentation.setEnabled(true);

        return userRepresentation;
    }

    private UserRepresentation buildBasicUserRepresentationForUpdate(Map<String, Object> params) {
        var firstName = (String) params.get(FIRST_NAME_MAP_KEY);
        var lastName = (String) params.get(LAST_NAME_MAP_KEY);
        var userName = (String) params.get(USERNAME_MAP_KEY);
        var email = (String) params.get(EMAIL_MAP_KEY);
        var enabled = (Boolean) params.get("enabled");

        if (StringUtils.isNotBlank(userName)) {
            throw new AdapterException("Username is not allowed to be edited!");
        }

        UserRepresentation userRepresentation = new UserRepresentation();

        if (StringUtils.isNotBlank(firstName)) {
            userRepresentation.setFirstName(firstName.trim());
        }

        if (StringUtils.isNotBlank(lastName)) {
            userRepresentation.setLastName(lastName.trim());
        }

        if (StringUtils.isNotBlank(email)) {
            userRepresentation.setEmail(email.trim());
        }

        userRepresentation.setEnabled(enabled);

        return userRepresentation;
    }

    private void addViewUsersRole(RealmResource realmResource, UsersResource usersResource, String username) {
        ClientsResource clientsResource = realmResource.clients();
        ClientRepresentation clientRepresentation = clientsResource.findByClientId(REALM_MANAGEMENT_CLIENT_ID).getFirst();
        RoleRepresentation roleRepresentation = clientsResource.get(clientRepresentation.getId()).roles().get(VIEW_USERS_ROLE_NAME).toRepresentation();

        List<RoleRepresentation> rolesToAdd = Collections.singletonList(roleRepresentation);
        List<UserRepresentation> userRepresentations = usersResource.searchByUsername(username, true);

        String userId = userRepresentations.getFirst().getId();

        usersResource.get(userId).roles().clientLevel(clientRepresentation.getId()).add(rolesToAdd);
    }

    private void addAdditionalAdminRoles(RealmResource realmResource, UserResource userResource) {
        ClientsResource clientsResource = realmResource.clients();
        ClientRepresentation clientRepresentation = clientsResource.findByClientId(REALM_MANAGEMENT_CLIENT_ID).getFirst();
        RolesResource rolesResource = clientsResource.get(clientRepresentation.getId()).roles();

        RoleRepresentation viewRealmRoleRepresentation = rolesResource.get(VIEW_REALM_ROLE_NAME).toRepresentation();
        RoleRepresentation viewClientsRoleRepresentation = rolesResource.get(VIEW_CLIENTS_ROLE_NAME).toRepresentation();
        RoleRepresentation manageUsersRoleRepresentation = rolesResource.get(MANAGE_USERS_ROLE_NAME).toRepresentation();

        List<RoleRepresentation> rolesToAdd = List.of(viewRealmRoleRepresentation, viewClientsRoleRepresentation, manageUsersRoleRepresentation);

        userResource.roles().clientLevel(clientRepresentation.getId()).add(rolesToAdd);
    }
}
