package io.littlehorse.usertasks.idp_adapters.keycloak;

import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.ACCESS_TOKEN_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.EMAIL_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.FIRST_NAME_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.FIRST_RESULT_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.LAST_NAME_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.MANAGE_USERS_ROLE_NAME;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.MAX_RESULTS_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.REALM_MANAGEMENT_CLIENT_ID;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USERNAME_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USER_GROUP_ID_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USER_GROUP_NAME_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USER_ID_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.VIEW_CLIENTS_ROLE_NAME;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.VIEW_REALM_ROLE_NAME;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.VIEW_USERS_ROLE_NAME;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.buildParamsForUserCreation;
import static io.littlehorse.usertasks.util.constants.AuthoritiesConstants.LH_USER_TASKS_ADMIN_ROLE;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateManagedUserRequest;
import io.littlehorse.usertasks.models.responses.IDPGroupDTO;
import io.littlehorse.usertasks.models.responses.IDPUserDTO;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import io.littlehorse.usertasks.models.responses.UserGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;
import jakarta.ws.rs.NotFoundException;
import jakarta.ws.rs.core.Response;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.ClientResource;
import org.keycloak.admin.client.resource.ClientsResource;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.RoleMappingResource;
import org.keycloak.admin.client.resource.RoleResource;
import org.keycloak.admin.client.resource.RoleScopeResource;
import org.keycloak.admin.client.resource.RolesResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.ClientMappingsRepresentation;
import org.keycloak.representations.idm.ClientRepresentation;
import org.keycloak.representations.idm.CredentialRepresentation;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.MappingsRepresentation;
import org.keycloak.representations.idm.RoleRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.util.CollectionUtils;
import org.springframework.web.server.ResponseStatusException;

@SuppressWarnings("unchecked")
class KeycloakAdapterTest {
    private final KeycloakAdapter keycloakAdapter = new KeycloakAdapter();

    private final String STUBBED_ACCESS_TOKEN =
            "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICIxemZidUhsMWtZX3k4bTFFaXFwdHVFNFNrbG1CNW"
                    + "JlaFpVN1ZVeG5TMzlBIn0.eyJleHAiOjE3MjE4NTU5ODQsImlhdCI6MTcyMTg1NTY4NCwianRpIjoiZGM4ZjVhOWUtOGRkZS00ZDY4LThmN"
                    + "GYtYTc5NzRhYzgwODgxIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4ODg4L3JlYWxtcy9saCIsImF1ZCI6WyJyZWFsbS1tYW5hZ2VtZW50"
                    + "IiwiYWNjb3VudCJdLCJzdWIiOiJlNWRmYmIwOC1lMzA4LTQwYjEtYmI5YS01YTI0NWQ1OTRiYzIiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJ"
                    + "1c2VyLXRhc2tzLWNsaWVudCIsInNlc3Npb25fc3RhdGUiOiJjZWE4NThiOC1mOTUwLTQzMmEtYWRlNi05MmJjNzIwMzRlNjIiLCJhY3IiOi"
                    + "IxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImxoLXVzZXItdGFza3MtYWRtaW4iLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZ"
                    + "XMtbGgiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmll"
                    + "dy11c2VycyIsInF1ZXJ5LWdyb3VwcyIsInF1ZXJ5LXVzZXJzIl19LCJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50Iiwidml"
                    + "ldy1ncm91cHMiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiZW1haWwgcHJvZmlsZSIsInNpZC"
                    + "I6ImNlYTg1OGI4LWY5NTAtNDMyYS1hZGU2LTkyYmM3MjAzNGU2MiIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuY"
                    + "W1lIjoibGgtYWRtaW4tdXNlciIsImdpdmVuX25hbWUiOiIiLCJmYW1pbHlfbmFtZSI6IiJ9.pTNWob_yMM5ECshFjum1wJ4P2NcBB6edsYB"
                    + "YKS6DQknx3HWRRgx0Sfi6gZiL_V9ehWk3O96ogzkA5SXyyfKrDf3Hbjj9om4eit9pXR1679toq4BTxUDvh46ERad1JzKHLo-sowzJB5o3by"
                    + "0kG4P5t3VwdaprlKgi6OhJbs8c2VMTjbFF_l5SeMlVno0wyMFGEDXQU6ENPgY7Jk29esTiizNmdigoxOgpK0wmDh4NJqe4TajO0cOKvkT1q"
                    + "p-0GFtpPXwUXn6Ghdsx_4Fwgox3SwDQdJUuit-UlTVjbjWyDgdWgB28Tn08K6MY-Yht_vPfqj1wgc7aArRDWjvZBGXRBA";

    private final Map<String, Object> standardParams = Map.of("accessToken", STUBBED_ACCESS_TOKEN);

    @Test
    void
            getUserGroups_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUserGroups(standardParams));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroups_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUserGroups(standardParams));

            var expectedErrorMessage = "Something went wrong while fetching all Groups from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroups_shouldReturnSetOfUserGroupNamesWhenNoExceptionIsThrown() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        GroupRepresentation group2 = new GroupRepresentation();
        group2.setId(UUID.randomUUID().toString());
        group2.setName("Group #2");
        var fakeGroups = List.of(group1, group2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(fakeGroups);

            UserGroupListDTO foundUserGroups = keycloakAdapter.getUserGroups(standardParams);

            int expectedQuantityOfGroups = 2;

            assertNotNull(foundUserGroups);
            assertFalse(foundUserGroups.getGroups().isEmpty());
            assertTrue(foundUserGroups.getGroups().stream()
                    .allMatch(userGroupDTO -> StringUtils.isNotBlank(userGroupDTO.getName())));
            assertEquals(expectedQuantityOfGroups, foundUserGroups.getGroups().size());
        }
    }

    @Test
    void getUserGroups_shouldReturnEmptySetOfUserGroupNamesWhenNoGroupsExist() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(Collections.emptyList());

            UserGroupListDTO foundUserGroups = keycloakAdapter.getUserGroups(standardParams);

            assertNotNull(foundUserGroups);
            assertTrue(foundUserGroups.getGroups().isEmpty());
        }
    }

    @Test
    void
            getMyUserGroups_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getMyUserGroups(standardParams));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getMyUserGroups_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getMyUserGroups(standardParams));

            var expectedErrorMessage = "Something went wrong while fetching all My Groups from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getMyUserGroups_shouldReturnSetOfUserGroupNamesWhenNoExceptionIsThrown() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        var fakeGroups = List.of(group1);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            UserGroupListDTO foundUserGroups = keycloakAdapter.getMyUserGroups(standardParams);

            int expectedQuantityOfGroups = 1;

            assertNotNull(foundUserGroups);
            assertFalse(foundUserGroups.getGroups().isEmpty());
            assertTrue(foundUserGroups.getGroups().stream()
                    .allMatch(userGroupDTO -> StringUtils.isNotBlank(userGroupDTO.getName())));
            assertEquals(expectedQuantityOfGroups, foundUserGroups.getGroups().size());
        }
    }

    @Test
    void getMyUserGroups_shouldReturnEmptySetOfUserGroupNamesWhenNoGroupsExist() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(Collections.emptyList());

            UserGroupListDTO foundUserGroups = keycloakAdapter.getMyUserGroups(standardParams);

            assertNotNull(foundUserGroups);
            assertTrue(foundUserGroups.getGroups().isEmpty());
        }
    }

    @Test
    void getUsers_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUsers(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUsers_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUsers(params));

            var expectedErrorMessage = "Something went wrong while fetching all Users from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUsers_shouldThrowExceptionWhenUserGroupIdAndUsernameAreReceived() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("userGroupId", "some-id");
        params.put("username", "some-username");
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUsers(params));

            var expectedErrorMessage =
                    "Combination of userGroup + other filters (username/email/firstName/lastName) is not supported";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsersWhenNoFilterIsAppliedAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        UserRepresentation user1 = new UserRepresentation();
        user1.setId(UUID.randomUUID().toString());
        user1.setUsername("username1");

        UserRepresentation user2 = new UserRepresentation();
        user2.setId(UUID.randomUUID().toString());
        user2.setUsername("username2");

        UserRepresentation user3 = new UserRepresentation();
        user3.setId(UUID.randomUUID().toString());
        user3.setUsername("username3");

        var fakeUsers = List.of(user1, user2, user3);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.list(anyInt(), anyInt())).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 3;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream().allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsersWhenUsernameFilterIsAppliedAndNoExceptionIsThrown() {
        var randomUsernamePrefix = "username";
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("username", randomUsernamePrefix);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        UserRepresentation user1 = new UserRepresentation();
        user1.setId(UUID.randomUUID().toString());
        user1.setUsername("username1");

        UserRepresentation user2 = new UserRepresentation();
        user2.setId(UUID.randomUUID().toString());
        user2.setUsername("username2");

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(randomUsernamePrefix),
                            eq(null),
                            eq(null),
                            eq(null),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO ->
                            StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getUsername())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsersWhenFirstNameFilterIsAppliedAndNoExceptionIsThrown() {
        var randomFirstNamePrefix = "al";
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstName", randomFirstNamePrefix);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        UserRepresentation user1 = new UserRepresentation();
        user1.setId(UUID.randomUUID().toString());
        user1.setFirstName("Albert");

        UserRepresentation user2 = new UserRepresentation();
        user2.setId(UUID.randomUUID().toString());
        user2.setFirstName("Allison");

        UserRepresentation user3 = new UserRepresentation();
        user3.setId(UUID.randomUUID().toString());
        user3.setFirstName("Alina");

        var fakeUsers = List.of(user1, user2, user3);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(null),
                            eq(randomFirstNamePrefix),
                            eq(null),
                            eq(null),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 3;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO ->
                            StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getFirstName())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsersWhenLastNameFilterIsAppliedAndNoExceptionIsThrown() {
        var randomLastNamePrefix = "own";
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("lastName", randomLastNamePrefix);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        UserRepresentation user1 = new UserRepresentation();
        user1.setId(UUID.randomUUID().toString());
        user1.setLastName("Brown");

        UserRepresentation user2 = new UserRepresentation();
        user2.setId(UUID.randomUUID().toString());
        user2.setLastName("Towns");

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(null),
                            eq(null),
                            eq(randomLastNamePrefix),
                            eq(null),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO ->
                            StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getLastName())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsersWhenEmailFilterIsAppliedAndNoExceptionIsThrown() {
        var randomEmailAddress = "somemail@somedomain.com";
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("email", randomEmailAddress);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        UserRepresentation user1 = new UserRepresentation();
        user1.setId(UUID.randomUUID().toString());
        user1.setUsername("username1");
        user1.setEmail(randomEmailAddress);

        var fakeUsers = List.of(user1);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(null),
                            eq(null),
                            eq(null),
                            eq(randomEmailAddress),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 1;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO ->
                            StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getEmail())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsersWhenUserGroupFilterIsAppliedAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("userGroupId", "my-group");
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);
        GroupResource fakeGroupResource = mock(GroupResource.class);

        UserRepresentation user1 = new UserRepresentation();
        user1.setId(UUID.randomUUID().toString());
        user1.setUsername("username1");

        UserRepresentation user2 = new UserRepresentation();
        user2.setId(UUID.randomUUID().toString());
        user2.setUsername("username2");

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.group(eq("my-group"))).thenReturn(fakeGroupResource);
            when(fakeGroupResource.members(anyInt(), anyInt())).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream().allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnEmptySetOfUsernamesWhenNoGroupsExist() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.list()).thenReturn(Collections.emptyList());

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            assertTrue(foundUsers.getUsers().isEmpty());
        }
    }

    @Test
    void
            getManagedUsers_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getManagedUsers(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getManagedUsers_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 5);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getManagedUsers(params));

            var expectedErrorMessage = "Something went wrong while fetching all managed Users from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getManagedUsers_shouldReturnSetOfUsersWhenNoFilterIsAppliedAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put("firstResult", 0);
        params.put("maxResults", 3);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        UserRepresentation user1 = getFakeUserRepresentation("username1");
        UserRepresentation user2 = getFakeUserRepresentation("username2");
        UserRepresentation user3 = getFakeUserRepresentation("username3");

        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation("custom-role", null, false);

        MappingsRepresentation fakeMappingsRepresentation = mock(MappingsRepresentation.class);
        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(Collections.emptyList());

        var fakeUsers = List.of(user1, user2, user3);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.list(anyInt(), anyInt())).thenReturn(fakeUsers);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(List.of());
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);
            when(fakeMappingsRepresentation.getClientMappings())
                    .thenReturn(Map.of("my-client", fakeClientMappingsRepresentation));

            IDPUserListDTO foundUsers = keycloakAdapter.getManagedUsers(params);

            int expectedQuantityOfUsers = 3;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())
                            && !CollectionUtils.isEmpty(userDTO.getRealmRoles())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getManagedUsers_shouldReturnSetOfUsersWhenSearchIsFilteredByEmailAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        String partialEmailToLookFor = "somedomain.com";
        params.put("email", partialEmailToLookFor);
        params.put("firstResult", 0);
        params.put("maxResults", 3);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        UserRepresentation user1 = getFakeUserRepresentation("username1");
        user1.setEmail("my-email@somedomain.com");

        UserRepresentation user2 = getFakeUserRepresentation("username2");
        user2.setEmail("my-other-email@somedomain.com");

        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation("custom-role", null, false);

        MappingsRepresentation fakeMappingsRepresentation = mock(MappingsRepresentation.class);
        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(Collections.emptyList());

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(null),
                            eq(null),
                            eq(null),
                            anyString(),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(List.of());
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);
            when(fakeMappingsRepresentation.getClientMappings())
                    .thenReturn(Map.of("my-client", fakeClientMappingsRepresentation));

            IDPUserListDTO foundUsers = keycloakAdapter.getManagedUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())
                            && !CollectionUtils.isEmpty(userDTO.getRealmRoles())
                            && StringUtils.contains(userDTO.getEmail(), partialEmailToLookFor)));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getManagedUsers_shouldReturnSetOfUsersWhenSearchIsFilteredByUsernameAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        String partialUsernameToLookFor = "mike1";
        params.put("username", partialUsernameToLookFor);
        params.put("firstResult", 0);
        params.put("maxResults", 3);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        UserRepresentation user1 = getFakeUserRepresentation("mike1990");
        UserRepresentation user2 = getFakeUserRepresentation("mike10");

        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation("custom-role", null, false);

        MappingsRepresentation fakeMappingsRepresentation = mock(MappingsRepresentation.class);
        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(Collections.emptyList());

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            anyString(),
                            eq(null),
                            eq(null),
                            eq(null),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(List.of());
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);
            when(fakeMappingsRepresentation.getClientMappings())
                    .thenReturn(Map.of("my-client", fakeClientMappingsRepresentation));

            IDPUserListDTO foundUsers = keycloakAdapter.getManagedUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())
                            && !CollectionUtils.isEmpty(userDTO.getRealmRoles())
                            && StringUtils.contains(userDTO.getUsername(), partialUsernameToLookFor)));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getManagedUsers_shouldReturnSetOfUsersWhenSearchIsFilteredByFirstNameAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        String partialFirstNameToLookFor = "Luc";
        params.put("firstName", partialFirstNameToLookFor);
        params.put("firstResult", 0);
        params.put("maxResults", 3);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        UserRepresentation user1 = getFakeUserRepresentation("username1");
        UserRepresentation user2 = getFakeUserRepresentation("username2");
        user1.setFirstName("Lucas");
        user2.setFirstName("Lucy");

        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation("custom-role", null, false);

        MappingsRepresentation fakeMappingsRepresentation = mock(MappingsRepresentation.class);
        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(Collections.emptyList());

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(null),
                            anyString(),
                            eq(null),
                            eq(null),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(List.of());
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);
            when(fakeMappingsRepresentation.getClientMappings())
                    .thenReturn(Map.of("my-client", fakeClientMappingsRepresentation));

            IDPUserListDTO foundUsers = keycloakAdapter.getManagedUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())
                            && !CollectionUtils.isEmpty(userDTO.getRealmRoles())
                            && StringUtils.contains(userDTO.getFirstName(), partialFirstNameToLookFor)));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getManagedUsers_shouldReturnSetOfUsersWhenSearchIsFilteredByLastNameAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        String partialLastNameToLookFor = "Mc";
        params.put("lastName", partialLastNameToLookFor);
        params.put("firstResult", 0);
        params.put("maxResults", 3);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        UserRepresentation user1 = getFakeUserRepresentation("username1");
        UserRepresentation user2 = getFakeUserRepresentation("username2");
        user1.setLastName("McDonald");
        user2.setLastName("McCarthy");

        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation("custom-role", null, false);

        MappingsRepresentation fakeMappingsRepresentation = mock(MappingsRepresentation.class);
        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(Collections.emptyList());

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(
                            eq(null),
                            eq(null),
                            anyString(),
                            eq(null),
                            anyInt(),
                            anyInt(),
                            anyBoolean(),
                            anyBoolean(),
                            anyBoolean()))
                    .thenReturn(fakeUsers);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(List.of());
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);
            when(fakeMappingsRepresentation.getClientMappings())
                    .thenReturn(Map.of("my-client", fakeClientMappingsRepresentation));

            IDPUserListDTO foundUsers = keycloakAdapter.getManagedUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())
                            && !CollectionUtils.isEmpty(userDTO.getRealmRoles())
                            && StringUtils.contains(userDTO.getLastName(), partialLastNameToLookFor)));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getManagedUsers_shouldReturnSetOfUsersWhenSearchIsFilteredByGroupIdAndNoExceptionIsThrown() {
        Map<String, Object> params = new HashMap<>(standardParams);
        String userGroupId = UUID.randomUUID().toString();
        params.put("userGroupId", userGroupId);
        params.put("firstResult", 0);
        params.put("maxResults", 3);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);
        GroupResource fakeGroupResource = mock(GroupResource.class);

        UserRepresentation user1 = getFakeUserRepresentation("username1");
        UserRepresentation user2 = getFakeUserRepresentation("username2");
        user1.setLastName("McDonald");
        user2.setLastName("McCarthy");

        GroupRepresentation fakeGroupRepresentation = new GroupRepresentation();
        fakeGroupRepresentation.setId(userGroupId);
        fakeGroupRepresentation.setName("my-group");

        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation("custom-role", null, false);

        MappingsRepresentation fakeMappingsRepresentation = mock(MappingsRepresentation.class);
        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(Collections.emptyList());

        var fakeUsers = List.of(user1, user2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.group(anyString())).thenReturn(fakeGroupResource);
            when(fakeGroupResource.members(anyInt(), anyInt())).thenReturn(fakeUsers);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(List.of(fakeGroupRepresentation));
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);
            when(fakeMappingsRepresentation.getClientMappings())
                    .thenReturn(Map.of("my-client", fakeClientMappingsRepresentation));

            IDPUserListDTO foundUsers = keycloakAdapter.getManagedUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())
                            && !CollectionUtils.isEmpty(userDTO.getRealmRoles())
                            && !CollectionUtils.isEmpty(userDTO.getGroups())
                            && StringUtils.equalsIgnoreCase(
                                    userDTO.getGroups().iterator().next().getId(), userGroupId)));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void
            getManagedUser_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put(USER_ID_MAP_KEY, UUID.randomUUID().toString());

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getManagedUser(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getManagedUser_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put(USER_ID_MAP_KEY, UUID.randomUUID().toString());

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getManagedUser(params));

            var expectedErrorMessage = "Something went wrong while fetching managed User from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getManagedUser_shouldReturnNullWhenNotFoundExceptionIsCaught() {
        String fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put(USER_ID_MAP_KEY, fakeUserId);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenThrow(new NotFoundException());

            assertNull(keycloakAdapter.getManagedUser(params));
        }
    }

    @Test
    void getManagedUser_shouldReturnIDPUserDTOWithRealmRolesWhenNoExceptionIsThrownAndUserRepresentationIsFound() {
        String fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put(USER_ID_MAP_KEY, fakeUserId);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);

        String fakeUsername = "some-username";
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId(fakeUserId);
        userRepresentation.setUsername(fakeUsername);
        userRepresentation.setEmail("someemail@somedomain.com");
        userRepresentation.setEnabled(true);

        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation();
        fakeRoleRepresentation.setId(UUID.randomUUID().toString());
        fakeRoleRepresentation.setName("myRole");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(userRepresentation);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRoleScopeResource.listAll()).thenReturn(List.of(fakeRoleRepresentation));

            IDPUserDTO userDTO = keycloakAdapter.getManagedUser(params);

            assertNotNull(userDTO);
            assertEquals(fakeUserId, userDTO.getId());
            assertTrue(StringUtils.equalsIgnoreCase(fakeUsername, userDTO.getUsername()));
            assertFalse(CollectionUtils.isEmpty(userDTO.getRealmRoles()));

            verify(fakeUserResource).toRepresentation();
        }
    }

    @Test
    void getManagedUser_shouldReturnIDPUserDTOWithClientRolesWhenNoExceptionIsThrownAndUserRepresentationIsFound() {
        String fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = new HashMap<>(standardParams);
        params.put(USER_ID_MAP_KEY, fakeUserId);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);

        String fakeUsername = "some-username";
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId(fakeUserId);
        userRepresentation.setUsername(fakeUsername);
        userRepresentation.setEmail("someemail@somedomain.com");
        userRepresentation.setEnabled(true);

        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation();
        fakeRoleRepresentation.setId(UUID.randomUUID().toString());
        fakeRoleRepresentation.setName("myRole");

        ClientMappingsRepresentation fakeClientMappingsRepresentation = new ClientMappingsRepresentation();
        fakeClientMappingsRepresentation.setId(UUID.randomUUID().toString());
        fakeClientMappingsRepresentation.setClient("my-client");
        fakeClientMappingsRepresentation.setMappings(List.of(fakeRoleRepresentation));

        MappingsRepresentation fakeMappingsRepresentation = new MappingsRepresentation();
        fakeMappingsRepresentation.setClientMappings(Map.of("my-client", fakeClientMappingsRepresentation));

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(userRepresentation);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.getAll()).thenReturn(fakeMappingsRepresentation);

            IDPUserDTO userDTO = keycloakAdapter.getManagedUser(params);

            assertNotNull(userDTO);
            assertEquals(fakeUserId, userDTO.getId());
            assertTrue(StringUtils.equalsIgnoreCase(fakeUsername, userDTO.getUsername()));
            assertTrue(CollectionUtils.isEmpty(userDTO.getRealmRoles()));
            assertFalse(CollectionUtils.isEmpty(userDTO.getClientRoles()));

            verify(fakeUserResource).toRepresentation();
        }
    }

    @Test
    void
            validateUserGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(
                    AdapterException.class, () -> keycloakAdapter.validateUserGroup("someGroup", STUBBED_ACCESS_TOKEN));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void validateUserGroup_shouldThrowResponseStatusExceptionAsForbiddenWhenNoUserGroupsAreFound() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(Collections.emptyList());

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class,
                    () -> keycloakAdapter.validateUserGroup("someGroup", STUBBED_ACCESS_TOKEN));

            int expectedHttpStatusCode = HttpStatus.FORBIDDEN.value();

            assertEquals(expectedHttpStatusCode, thrownException.getBody().getStatus());
        }
    }

    @Test
    void validateUserGroup_shouldThrowResponseStatusExceptionAsForbiddenWhenNoMatchingUserGroupIsFound() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        GroupRepresentation group2 = new GroupRepresentation();
        group2.setId(UUID.randomUUID().toString());
        group2.setName("Group #2");

        var fakeGroups = List.of(group1, group2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class,
                    () -> keycloakAdapter.validateUserGroup("someGroup", STUBBED_ACCESS_TOKEN));

            int expectedHttpStatusCode = HttpStatus.FORBIDDEN.value();

            assertEquals(expectedHttpStatusCode, thrownException.getBody().getStatus());
        }
    }

    @Test
    void validateUserGroup_shouldNotThrowAnyExceptionWhenMatchingUserGroupIsFound() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        var idGroup2 = UUID.randomUUID().toString();

        GroupRepresentation group2 = new GroupRepresentation();
        group2.setId(idGroup2);
        group2.setName("Group #2");

        var fakeGroups = List.of(group1, group2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            assertDoesNotThrow(() -> keycloakAdapter.validateUserGroup(idGroup2, STUBBED_ACCESS_TOKEN));
        }
    }

    @Test
    void
            getUserInfo_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var userId = "someUserId";
        Map<String, Object> params = Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUserInfo(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserInfo_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var userId = "someUserId";
        Map<String, Object> params = Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUserInfo(params));

            var expectedErrorMessage = "Something went wrong while fetching User's info from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserInfo_shouldReturnUserDTOWhenUserRepresentationIsFoundUsingUserId() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(fakeUserRepresentation);

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNotNull(foundUserInfo);
            assertTrue(StringUtils.isNotBlank(foundUserInfo.getId()));
        }
    }

    @Test
    void getUserInfo_shouldReturnUserDTOWhenUserRepresentationIsFoundUsingEmail() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                "userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "email", "someaddress@somedomain.com");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByEmail(anyString(), anyBoolean()))
                    .thenReturn(List.of(fakeUserRepresentation));

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNotNull(foundUserInfo);
            assertTrue(StringUtils.isNotBlank(foundUserInfo.getId()));
        }
    }

    @Test
    void getUserInfo_shouldReturnNullWhenUserRepresentationIsNotFoundUsingEmail() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                "userId",
                userId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                "email",
                "someweirdaddress@somedomain.co");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByEmail(anyString(), anyBoolean())).thenReturn(Collections.emptyList());

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNull(foundUserInfo);
        }
    }

    @Test
    void getUserInfo_shouldReturnUserDTOWhenUserRepresentationIsFoundUsingUsername() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params =
                Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "username", "my-username");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByUsername(anyString(), anyBoolean()))
                    .thenReturn(List.of(fakeUserRepresentation));

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNotNull(foundUserInfo);
            assertTrue(StringUtils.isNotBlank(foundUserInfo.getId()));
        }
    }

    @Test
    void getUserInfo_shouldReturnNullWhenUserRepresentationIsNotFoundUsingUsername() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params =
                Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "username", "my-username");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByUsername(anyString(), anyBoolean())).thenReturn(Collections.emptyList());

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNull(foundUserInfo);
        }
    }

    @Test
    void
            getUserGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userGroupId", userGroupId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUserGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userGroupId", userGroupId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getUserGroup(params));

            var expectedErrorMessage = "Something went wrong while fetching Group's info from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroup_shouldReturnNullWhenUserGroupRepresentationIsNotFound() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userGroupName", userGroupId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.query(anyString())).thenThrow(new NotFoundException());

            UserGroupDTO userGroup = keycloakAdapter.getUserGroup(params);

            assertNull(userGroup);
        }
    }

    @Test
    void getUserGroup_shouldReturnNullWhenUserGroupIsNotFoundUsingUserGroupName() {
        var userGroupNameParam = "some-group";
        Map<String, Object> params =
                Map.of("userGroupName", userGroupNameParam, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.query(anyString())).thenReturn(Collections.emptyList());

            UserGroupDTO userGroup = keycloakAdapter.getUserGroup(params);

            assertNull(userGroup);
        }
    }

    @Test
    void getUserGroup_shouldReturnNullWhenUserGroupNameDoesMatchAnyFoundGroups() {
        var userGroupNameParam = "some-group";
        Map<String, Object> params =
                Map.of("userGroupName", userGroupNameParam, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);
        GroupRepresentation fakeGroupRepresentation = new GroupRepresentation();
        fakeGroupRepresentation.setId(UUID.randomUUID().toString());
        fakeGroupRepresentation.setName("some-group-name");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.query(anyString())).thenReturn(List.of(fakeGroupRepresentation));

            UserGroupDTO userGroup = keycloakAdapter.getUserGroup(params);

            assertNull(userGroup);
        }
    }

    @Test
    void getUserGroup_shouldReturnUserGroupDTOWhenUserGroupRepresentationIsFound() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params =
                Map.of("userGroupName", "some-group-name", ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);
        GroupRepresentation fakeGroupRepresentation = new GroupRepresentation();
        fakeGroupRepresentation.setId(userGroupId);
        fakeGroupRepresentation.setName("some-group-name");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.query(anyString())).thenReturn(List.of(fakeGroupRepresentation));

            UserGroupDTO userGroup = keycloakAdapter.getUserGroup(params);

            assertNotNull(userGroup);
            assertTrue(StringUtils.isNotBlank(userGroup.getId()));
            assertTrue(StringUtils.isNotBlank(userGroup.getName()));
        }
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenNoParamsAreProvided() {
        ResponseStatusException thrownException = assertThrows(
                ResponseStatusException.class,
                () -> keycloakAdapter.validateAssignmentProperties(Collections.emptyMap()));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
        var expectedErrorMessage = "No valid arguments were received to complete reassignment.";

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals(expectedErrorMessage, thrownException.getReason());
    }

    @Test
    void
            validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenNoUserAndUserGroupAreProvided() {
        Map<String, Object> params = Map.of("someKey", new Object());

        ResponseStatusException thrownException =
                assertThrows(ResponseStatusException.class, () -> keycloakAdapter.validateAssignmentProperties(params));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
        var expectedErrorMessage = "No valid arguments were received to complete reassignment.";

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals(expectedErrorMessage, thrownException.getReason());
    }

    @Test
    void
            validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdIsReceivedButNoUserInfoIsFound() {
        var userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenThrow(new NotFoundException());

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class, () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent user.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void
            validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserGroupIsReceivedButItIsNotFoundWithinRealm() {
        var requestedUserGroup = "my-group";
        Map<String, Object> params =
                Map.of("userGroupId", requestedUserGroup, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(Collections.emptyList());

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class, () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent group, nor can the Task be assigned to an "
                    + "existing group that the requested user is not a member of.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void
            validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserGroupIsReceivedButItIsNotPartOfTheOnesInRealm() {
        var requestedUserGroup = "my-group";
        Map<String, Object> params =
                Map.of("userGroupId", requestedUserGroup, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        GroupRepresentation group2 = new GroupRepresentation();
        group2.setId(UUID.randomUUID().toString());
        group2.setName("Group #2");
        var fakeGroups = List.of(group1, group2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(fakeGroups);

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class, () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent group, nor can the Task be assigned to an "
                    + "existing group that the requested user is not a member of.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void
            validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdAndUserGroupAreReceivedButUserGroupIsNotFoundWithinRealm() {
        var userId = UUID.randomUUID().toString();
        var requestedUserGroup = "my-group";
        Map<String, Object> params =
                Map.of("userId", userId, "userGroupId", requestedUserGroup, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(fakeUserRepresentation);

            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(Collections.emptyList());

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class, () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent group, nor can the Task be assigned to an "
                    + "existing group that the requested user is not a member of.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void
            validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdAndUserGroupAreReceivedButNoUserInfoIsFound() {
        var userId = UUID.randomUUID().toString();
        var requestedUserGroup = "my-group";
        Map<String, Object> params =
                Map.of("userId", userId, "userGroupId", requestedUserGroup, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenThrow(new NotFoundException());

            ResponseStatusException thrownException = assertThrows(
                    ResponseStatusException.class, () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent user.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void
            validateAssignmentProperties_shouldNotThrowAnyExceptionWhenUserIdAndUserGroupAreReceivedAndTheyAreFoundWithinRealm() {
        var userId = UUID.randomUUID().toString();
        var requestedUserGroup = UUID.randomUUID().toString();
        Map<String, Object> params =
                Map.of("userId", userId, "userGroupId", requestedUserGroup, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        GroupRepresentation group2 = new GroupRepresentation();
        group2.setId(requestedUserGroup);
        group2.setName("Group #2");
        var fakeGroups = List.of(group1, group2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(fakeUserRepresentation);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            assertDoesNotThrow(() -> keycloakAdapter.validateAssignmentProperties(params));
        }
    }

    @Test
    void validateAssignmentProperties_shouldNotThrowAnyExceptionWhenUserGroupIsReceivedAndItIsFoundWithinRealm() {
        var requestedUserGroup = UUID.randomUUID().toString();
        Map<String, Object> params =
                Map.of("userGroupId", requestedUserGroup, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        GroupRepresentation group1 = new GroupRepresentation();
        group1.setId(UUID.randomUUID().toString());
        group1.setName("Group #1");

        GroupRepresentation group2 = new GroupRepresentation();
        group2.setId(requestedUserGroup);
        group2.setName("Group #2");
        var fakeGroups = List.of(group1, group2);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(fakeGroups);

            assertDoesNotThrow(() -> keycloakAdapter.validateAssignmentProperties(params));
        }
    }

    @Test
    void validateAssignmentProperties_shouldNotThrowAnyExceptionWhenUserIdIsReceivedAndItIsFoundWithinRealm() {
        var userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(fakeUserRepresentation);

            assertDoesNotThrow(() -> keycloakAdapter.validateAssignmentProperties(params));
        }
    }

    @Test
    void buildParamsForUserCreation_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        assertThrows(
                NullPointerException.class,
                () -> KeycloakAdapter.buildParamsForUserCreation(
                        null, CreateManagedUserRequest.builder().build()));
    }

    @Test
    void buildParamsForUserCreation_shouldThrowNullPointerExceptionWhenNullRequestObjectIsReceived() {
        assertThrows(
                NullPointerException.class,
                () -> KeycloakAdapter.buildParamsForUserCreation(STUBBED_ACCESS_TOKEN, null));
    }

    @Test
    void buildParamsForUserCreation_shouldThrowNullPointerExceptionIfRequestObjectHasBlankUsername() {
        CreateManagedUserRequest request =
                CreateManagedUserRequest.builder().username("   ").build();

        AdapterException adapterException =
                assertThrows(AdapterException.class, () -> buildParamsForUserCreation(STUBBED_ACCESS_TOKEN, request));

        String expectedErrorMessage = "Cannot create User without username!";
        assertEquals(expectedErrorMessage, adapterException.getMessage());
    }

    @Test
    void buildParamsForUserCreation_shouldReturnTwoParamsIfUsernameIsPresentWithinRequestNotNullNorBlank() {
        var fakeUsername = "someUsername";

        CreateManagedUserRequest request = CreateManagedUserRequest.builder()
                .firstName(" ")
                .lastName(null)
                .username(fakeUsername)
                .email("")
                .build();

        Map<String, Object> params = buildParamsForUserCreation(STUBBED_ACCESS_TOKEN, request);

        int expectedParamsCount = 2;

        assertFalse(params.isEmpty());
        assertEquals(expectedParamsCount, params.size());
        assertEquals(STUBBED_ACCESS_TOKEN, params.get(ACCESS_TOKEN_MAP_KEY));
        assertEquals(fakeUsername, params.get(USERNAME_MAP_KEY));
    }

    @Test
    void buildParamsForUserCreation_shouldReturnAllParamsIfPresentAndNotNullNorBlank() {
        var fakeFirstName = "someName";
        var fakeLastName = "someLastName";
        var fakeUsername = "someUsername";
        var fakeEmail = "someemail@somedomain.com";

        CreateManagedUserRequest request = CreateManagedUserRequest.builder()
                .firstName(fakeFirstName)
                .lastName(fakeLastName)
                .username(fakeUsername)
                .email(fakeEmail)
                .build();

        Map<String, Object> params = buildParamsForUserCreation(STUBBED_ACCESS_TOKEN, request);

        int expectedParamsCount = 5;

        assertFalse(params.isEmpty());
        assertEquals(expectedParamsCount, params.size());
        assertEquals(STUBBED_ACCESS_TOKEN, params.get(ACCESS_TOKEN_MAP_KEY));
        assertEquals(fakeFirstName, params.get(FIRST_NAME_MAP_KEY));
        assertEquals(fakeLastName, params.get(LAST_NAME_MAP_KEY));
        assertEquals(fakeUsername, params.get(USERNAME_MAP_KEY));
        assertEquals(fakeEmail, params.get(EMAIL_MAP_KEY));
    }

    @Test
    void
            createManagedUser_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUsername = "someUsername";
        Map<String, Object> params = Map.of(USERNAME_MAP_KEY, fakeUsername, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createManagedUser(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void createManagedUser_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUsername = "someUsername";
        Map<String, Object> params = Map.of(USERNAME_MAP_KEY, fakeUsername, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createManagedUser(params));

            var expectedErrorMessage = "Something went wrong while creating a User in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void createManagedUser_shouldThrowExceptionCreatingKeycloakInstanceWhenUsernameIsNotPresent() {
        var fakeEmail = "somemail@somedomain.com";
        Map<String, Object> params = Map.of(EMAIL_MAP_KEY, fakeEmail, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createManagedUser(params));

            var expectedErrorMessage = "Cannot create User without username!";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void createManagedUser_shouldThrowExceptionWhenResponseIsNotCreated() {
        var fakeUsername = "someUsername";
        Map<String, Object> params = Map.of(USERNAME_MAP_KEY, fakeUsername, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        Response fakeResponse = Response.created(URI.create("www.some-fake-uri.com"))
                .status(HttpStatus.BAD_REQUEST.value())
                .build();

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.create(any(UserRepresentation.class))).thenReturn(fakeResponse);

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createManagedUser(params));

            var expectedErrorMessage = "User creation failed within realm lh with status: 400!";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void
            createManagedUser_shouldSucceedWhenCreatingManagedUserOnlyWithUsernameNoExceptionsAreThrownAndResponseIsCreated() {
        var fakeUsername = "someUsername";
        var fakeClientRepresentationId = UUID.randomUUID().toString();
        var fakeViewUsersRoleId = UUID.randomUUID().toString();
        var fakeUserId = UUID.randomUUID().toString();

        Map<String, Object> params = Map.of(USERNAME_MAP_KEY, fakeUsername, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        ClientsResource fakeClientsResource = mock(ClientsResource.class);
        ClientResource fakeClientResource = mock(ClientResource.class);
        RolesResource fakeRolesResource = mock(RolesResource.class);
        RoleResource fakeRoleResource = mock(RoleResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);

        ClientRepresentation fakeClientRepresentation = new ClientRepresentation();
        fakeClientRepresentation.setId(fakeClientRepresentationId);
        fakeClientRepresentation.setClientId(REALM_MANAGEMENT_CLIENT_ID);

        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation();
        fakeRoleRepresentation.setId(fakeViewUsersRoleId);

        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(fakeUserId);
        fakeUserRepresentation.setUsername(fakeUsername);

        Response fakeResponse = Response.created(URI.create("www.some-fake-uri.com"))
                .status(HttpStatus.CREATED.value())
                .build();

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.create(any(UserRepresentation.class))).thenReturn(fakeResponse);
            when(fakeRealmResource.clients()).thenReturn(fakeClientsResource);
            when(fakeClientsResource.findByClientId(eq(REALM_MANAGEMENT_CLIENT_ID)))
                    .thenReturn(Collections.singletonList(fakeClientRepresentation));
            when(fakeClientsResource.get(eq(fakeClientRepresentationId))).thenReturn(fakeClientResource);
            when(fakeClientResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRolesResource.get(eq(VIEW_USERS_ROLE_NAME))).thenReturn(fakeRoleResource);
            when(fakeRoleResource.toRepresentation()).thenReturn(fakeRoleRepresentation);
            when(fakeUsersResource.searchByUsername(eq(fakeUsername), eq(true)))
                    .thenReturn(Collections.singletonList(fakeUserRepresentation));
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.clientLevel(eq(fakeClientRepresentationId)))
                    .thenReturn(fakeRoleScopeResource);

            assertDoesNotThrow(() -> keycloakAdapter.createManagedUser(params));

            verify(fakeUsersResource).create(any(UserRepresentation.class));
            verify(fakeRoleScopeResource).add(anyList());
        }
    }

    @Test
    void
            createManagedUser_shouldSucceedWhenCreatingManagedUserOnlyWithUsernameAndEmailNoExceptionsAreThrownAndResponseIsCreated() {
        var fakeUsername = "someUsername";
        var fakeEmail = "somemail@somedomain.com";
        var fakeClientRepresentationId = UUID.randomUUID().toString();
        var fakeViewUsersRoleId = UUID.randomUUID().toString();
        var fakeUserId = UUID.randomUUID().toString();

        Map<String, Object> params = Map.of(
                USERNAME_MAP_KEY, fakeUsername, EMAIL_MAP_KEY, fakeEmail, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        ClientsResource fakeClientsResource = mock(ClientsResource.class);
        ClientResource fakeClientResource = mock(ClientResource.class);
        RolesResource fakeRolesResource = mock(RolesResource.class);
        RoleResource fakeRoleResource = mock(RoleResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);

        ClientRepresentation fakeClientRepresentation = new ClientRepresentation();
        fakeClientRepresentation.setId(fakeClientRepresentationId);
        fakeClientRepresentation.setClientId(REALM_MANAGEMENT_CLIENT_ID);

        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation();
        fakeRoleRepresentation.setId(fakeViewUsersRoleId);

        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(fakeUserId);
        fakeUserRepresentation.setUsername(fakeUsername);

        Response fakeResponse = Response.created(URI.create("www.some-fake-uri.com"))
                .status(HttpStatus.CREATED.value())
                .build();

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.create(any(UserRepresentation.class))).thenReturn(fakeResponse);
            when(fakeRealmResource.clients()).thenReturn(fakeClientsResource);
            when(fakeClientsResource.findByClientId(eq(REALM_MANAGEMENT_CLIENT_ID)))
                    .thenReturn(Collections.singletonList(fakeClientRepresentation));
            when(fakeClientsResource.get(eq(fakeClientRepresentationId))).thenReturn(fakeClientResource);
            when(fakeClientResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRolesResource.get(eq(VIEW_USERS_ROLE_NAME))).thenReturn(fakeRoleResource);
            when(fakeRoleResource.toRepresentation()).thenReturn(fakeRoleRepresentation);
            when(fakeUsersResource.searchByUsername(eq(fakeUsername), eq(true)))
                    .thenReturn(Collections.singletonList(fakeUserRepresentation));
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.clientLevel(eq(fakeClientRepresentationId)))
                    .thenReturn(fakeRoleScopeResource);

            assertDoesNotThrow(() -> keycloakAdapter.createManagedUser(params));

            verify(fakeUsersResource).create(any(UserRepresentation.class));
            verify(fakeRoleScopeResource).add(anyList());
        }
    }

    @Test
    void
            createManagedUser_shouldSucceedWhenCreatingManagedUserWithUsernameEmailFirstNameAndLastNameNoExceptionsAreThrownAndResponseIsCreated() {
        var fakeUsername = "someUsername";
        var fakeEmail = "somemail@somedomain.com";
        var firstName = "Name";
        var lastName = "LastName";
        var fakeClientRepresentationId = UUID.randomUUID().toString();
        var fakeViewUsersRoleId = UUID.randomUUID().toString();
        var fakeUserId = UUID.randomUUID().toString();

        Map<String, Object> params = Map.of(
                USERNAME_MAP_KEY,
                fakeUsername,
                EMAIL_MAP_KEY,
                fakeEmail,
                FIRST_NAME_MAP_KEY,
                firstName,
                LAST_NAME_MAP_KEY,
                lastName,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        ClientsResource fakeClientsResource = mock(ClientsResource.class);
        ClientResource fakeClientResource = mock(ClientResource.class);
        RolesResource fakeRolesResource = mock(RolesResource.class);
        RoleResource fakeRoleResource = mock(RoleResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
        RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);

        ClientRepresentation fakeClientRepresentation = new ClientRepresentation();
        fakeClientRepresentation.setId(fakeClientRepresentationId);
        fakeClientRepresentation.setClientId(REALM_MANAGEMENT_CLIENT_ID);

        RoleRepresentation fakeRoleRepresentation = new RoleRepresentation();
        fakeRoleRepresentation.setId(fakeViewUsersRoleId);

        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(fakeUserId);
        fakeUserRepresentation.setUsername(fakeUsername);

        Response fakeResponse = Response.created(URI.create("www.some-fake-uri.com"))
                .status(HttpStatus.CREATED.value())
                .build();

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.create(any(UserRepresentation.class))).thenReturn(fakeResponse);
            when(fakeRealmResource.clients()).thenReturn(fakeClientsResource);
            when(fakeClientsResource.findByClientId(eq(REALM_MANAGEMENT_CLIENT_ID)))
                    .thenReturn(Collections.singletonList(fakeClientRepresentation));
            when(fakeClientsResource.get(eq(fakeClientRepresentationId))).thenReturn(fakeClientResource);
            when(fakeClientResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRolesResource.get(eq(VIEW_USERS_ROLE_NAME))).thenReturn(fakeRoleResource);
            when(fakeRoleResource.toRepresentation()).thenReturn(fakeRoleRepresentation);
            when(fakeUsersResource.searchByUsername(eq(fakeUsername), eq(true)))
                    .thenReturn(Collections.singletonList(fakeUserRepresentation));
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.clientLevel(eq(fakeClientRepresentationId)))
                    .thenReturn(fakeRoleScopeResource);

            assertDoesNotThrow(() -> keycloakAdapter.createManagedUser(params));

            verify(fakeUsersResource).create(any(UserRepresentation.class));
            verify(fakeRoleScopeResource).add(anyList());
        }
    }

    @Test
    void
            setPassword_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", false);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.setPassword(fakeUserId, params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void setPassword_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", false);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.setPassword(fakeUserId, params));

            var expectedErrorMessage = "Something went wrong while setting a User's password in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void setPassword_shouldThrowExceptionWhenNotFoundExceptionIsCaught() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", false);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenThrow(new NotFoundException());

            io.littlehorse.usertasks.exceptions.NotFoundException thrownException = assertThrows(
                    io.littlehorse.usertasks.exceptions.NotFoundException.class,
                    () -> keycloakAdapter.setPassword(fakeUserId, params));

            var expectedErrorMessage = "User could not be found.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void setPassword_shouldSucceedWhenNoPreviousCredentialsArePresentAndPasswordIsNotTemporary() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", false);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setId(fakeUserId);

            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(userRepresentation);

            keycloakAdapter.setPassword(fakeUserId, params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationSent = argumentCaptor.getValue();

            int expectedCredentialsCount = 1;
            List<CredentialRepresentation> credentialsSent = userRepresentationSent.getCredentials();

            assertNotNull(userRepresentationSent);
            assertEquals(expectedCredentialsCount, credentialsSent.size());
            assertEquals(fakePassword, credentialsSent.getFirst().getValue());
            assertFalse(credentialsSent.getFirst().isTemporary());
        }
    }

    @Test
    void setPassword_shouldSucceedWhenNoPreviousCredentialsArePresentAndPasswordIsTemporary() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", true);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setId(fakeUserId);

            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(userRepresentation);

            keycloakAdapter.setPassword(fakeUserId, params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationSent = argumentCaptor.getValue();

            int expectedCredentialsCount = 1;
            List<CredentialRepresentation> credentialsSent = userRepresentationSent.getCredentials();

            assertNotNull(userRepresentationSent);
            assertEquals(expectedCredentialsCount, credentialsSent.size());
            assertEquals(fakePassword, credentialsSent.getFirst().getValue());
            assertTrue(credentialsSent.getFirst().isTemporary());
        }
    }

    @Test
    void setPassword_shouldSucceedWhenPreviousCredentialsArePresentAndPasswordIsTemporary() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", true);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setId(fakeUserId);

            CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
            credentialRepresentation.setId(UUID.randomUUID().toString());

            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.credentials()).thenReturn(List.of(credentialRepresentation));

            keycloakAdapter.setPassword(fakeUserId, params);

            ArgumentCaptor<CredentialRepresentation> argumentCaptor =
                    ArgumentCaptor.forClass(CredentialRepresentation.class);

            verify(fakeUserResource).resetPassword(argumentCaptor.capture());

            CredentialRepresentation credentialsUpdated = argumentCaptor.getValue();

            assertNotNull(credentialsUpdated);
            assertEquals(fakePassword, credentialsUpdated.getValue());
            assertTrue(credentialsUpdated.isTemporary());
        }
    }

    @Test
    void setPassword_shouldSucceedWhenPreviousCredentialsArePresentAndPasswordIsNotTemporary() {
        var fakeUserId = "someUserId";
        var fakePassword = "somePassword";
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "password", fakePassword, "isTemporary", false);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            UserRepresentation userRepresentation = new UserRepresentation();
            userRepresentation.setId(fakeUserId);

            CredentialRepresentation credentialRepresentation = new CredentialRepresentation();
            credentialRepresentation.setId(UUID.randomUUID().toString());

            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeUserResource.credentials()).thenReturn(List.of(credentialRepresentation));

            keycloakAdapter.setPassword(fakeUserId, params);

            ArgumentCaptor<CredentialRepresentation> argumentCaptor =
                    ArgumentCaptor.forClass(CredentialRepresentation.class);

            verify(fakeUserResource).resetPassword(argumentCaptor.capture());

            CredentialRepresentation credentialsUpdated = argumentCaptor.getValue();

            assertNotNull(credentialsUpdated);
            assertEquals(fakePassword, credentialsUpdated.getValue());
            assertFalse(credentialsUpdated.isTemporary());
        }
    }

    @Test
    void
            updateManagedUser_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeEmail = "someemail@somedomain.com";
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, EMAIL_MAP_KEY, fakeEmail);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.updateManagedUser(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void updateManagedUser_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeEmail = "someemail@somedomain.com";
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, EMAIL_MAP_KEY, fakeEmail);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.updateManagedUser(params));

            var expectedErrorMessage = "Something went wrong while creating a User in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void updateManagedUser_shouldThrowExceptionWhenTryingToUpdateUsername() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeEmail = "someemail@somedomain.com";
        var fakeUsername = "someUsername";

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                EMAIL_MAP_KEY,
                fakeEmail,
                USERNAME_MAP_KEY,
                fakeUsername);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.updateManagedUser(params));

            var expectedErrorMessage = "Username is not allowed to be edited!";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void updateManagedUser_shouldSucceedWhenUpdatingFirstNameAndNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeFirstName = "someName";
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                FIRST_NAME_MAP_KEY,
                fakeFirstName,
                "enabled",
                true);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.updateManagedUser(params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationToBeUpdated = argumentCaptor.getValue();

            assertNotNull(userRepresentationToBeUpdated);
            assertTrue(StringUtils.equalsIgnoreCase(fakeFirstName, userRepresentationToBeUpdated.getFirstName()));
            assertTrue(userRepresentationToBeUpdated.isEnabled());
        }
    }

    @Test
    void updateManagedUser_shouldSucceedWhenUpdatingLastNameAndNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeLastName = "someLastName";
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                LAST_NAME_MAP_KEY,
                fakeLastName,
                "enabled",
                true);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.updateManagedUser(params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationToBeUpdated = argumentCaptor.getValue();

            assertNotNull(userRepresentationToBeUpdated);
            assertTrue(StringUtils.equalsIgnoreCase(fakeLastName, userRepresentationToBeUpdated.getLastName()));
            assertTrue(userRepresentationToBeUpdated.isEnabled());
        }
    }

    @Test
    void updateManagedUser_shouldSucceedWhenUpdatingEmailAndNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeEmail = "someemail@someaddress.com";
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                EMAIL_MAP_KEY,
                fakeEmail,
                "enabled",
                true);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.updateManagedUser(params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationToBeUpdated = argumentCaptor.getValue();

            assertNotNull(userRepresentationToBeUpdated);
            assertTrue(StringUtils.equalsIgnoreCase(fakeEmail, userRepresentationToBeUpdated.getEmail()));
            assertTrue(userRepresentationToBeUpdated.isEnabled());
        }
    }

    @Test
    void updateManagedUser_shouldSucceedWhenUpdatingEnabledAndNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        Map<String, Object> params =
                Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, "enabled", false);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.updateManagedUser(params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationToBeUpdated = argumentCaptor.getValue();

            assertNotNull(userRepresentationToBeUpdated);
            assertFalse(userRepresentationToBeUpdated.isEnabled());
        }
    }

    @Test
    void updateManagedUser_shouldSucceedWhenUpdatingAllBasicFieldsAndNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeEmail = "somerandommail@somedomain.com";
        var fakeFirstName = "someName";
        var fakeLastName = "someLastName";
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                FIRST_NAME_MAP_KEY,
                fakeFirstName,
                LAST_NAME_MAP_KEY,
                fakeLastName,
                EMAIL_MAP_KEY,
                fakeEmail);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.updateManagedUser(params);

            ArgumentCaptor<UserRepresentation> argumentCaptor = ArgumentCaptor.forClass(UserRepresentation.class);

            verify(fakeUserResource).update(argumentCaptor.capture());

            UserRepresentation userRepresentationToBeUpdated = argumentCaptor.getValue();

            assertNotNull(userRepresentationToBeUpdated);
            assertTrue(StringUtils.equalsIgnoreCase(fakeFirstName, userRepresentationToBeUpdated.getFirstName()));
            assertTrue(StringUtils.equalsIgnoreCase(fakeLastName, userRepresentationToBeUpdated.getLastName()));
            assertTrue(StringUtils.equalsIgnoreCase(fakeEmail, userRepresentationToBeUpdated.getEmail()));
        }
    }

    @Test
    void
            deleteManagedUser_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.deleteManagedUser(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void deleteManagedUser_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.deleteManagedUser(params));

            var expectedErrorMessage = "Something went wrong while deleting a User from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void deleteManagedUser_shouldThrowExceptionWhenResponseIsDifferentFromNoContent() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealm = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealm);
            when(fakeRealm.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.delete(eq(fakeUserId)))
                    .thenReturn(Response.serverError().build());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.deleteManagedUser(params));

            var expectedErrorMessage = "User deletion failed in Keycloak!";

            assertEquals(expectedErrorMessage, thrownException.getMessage());

            verify(fakeUsersResource).delete(eq(fakeUserId));
        }
    }

    @Test
    void deleteManagedUser_shouldSucceedWhenNoExceptionsAreThrownAndResponseIsNoContent() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.delete(eq(fakeUserId)))
                    .thenReturn(Response.noContent().build());

            keycloakAdapter.deleteManagedUser(params);

            verify(fakeUsersResource).delete(eq(fakeUserId));
        }
    }

    @Test
    void
            assignAdminRole_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.assignAdminRole(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void assignAdminRole_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.assignAdminRole(params));

            var expectedErrorMessage = "Something went wrong while assigning admin role to user in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void assignAdminRole_shouldSucceedWhenNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeClientRepresentationId = UUID.randomUUID().toString();

        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);
            RolesResource fakeRolesResource = mock(RolesResource.class);
            RoleResource fakeRoleResource = mock(RoleResource.class);
            RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
            RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
            ClientsResource fakeClientsResource = mock(ClientsResource.class);
            ClientResource fakeClientResource = mock(ClientResource.class);

            RoleRepresentation fakeUTBAdminRoleRepresentation = new RoleRepresentation();
            fakeUTBAdminRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeUTBAdminRoleRepresentation.setName(LH_USER_TASKS_ADMIN_ROLE);

            RoleRepresentation fakeViewRealmRoleRepresentation = new RoleRepresentation();
            fakeViewRealmRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeViewRealmRoleRepresentation.setName(VIEW_REALM_ROLE_NAME);

            RoleRepresentation fakeViewClientsRoleRepresentation = new RoleRepresentation();
            fakeViewClientsRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeViewClientsRoleRepresentation.setName(VIEW_CLIENTS_ROLE_NAME);

            RoleRepresentation fakeManageUsersRoleRepresentation = new RoleRepresentation();
            fakeManageUsersRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeManageUsersRoleRepresentation.setName(MANAGE_USERS_ROLE_NAME);

            ClientRepresentation fakeClientRepresentation = new ClientRepresentation();
            fakeClientRepresentation.setId(fakeClientRepresentationId);
            fakeClientRepresentation.setClientId(REALM_MANAGEMENT_CLIENT_ID);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeRealmResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRolesResource.get(anyString())).thenReturn(fakeRoleResource);
            when(fakeRoleResource.toRepresentation())
                    .thenReturn(
                            fakeUTBAdminRoleRepresentation,
                            fakeViewRealmRoleRepresentation,
                            fakeViewClientsRoleRepresentation,
                            fakeManageUsersRoleRepresentation);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRealmResource.clients()).thenReturn(fakeClientsResource);
            when(fakeClientsResource.findByClientId(eq(REALM_MANAGEMENT_CLIENT_ID)))
                    .thenReturn(Collections.singletonList(fakeClientRepresentation));
            when(fakeClientsResource.get(eq(fakeClientRepresentationId))).thenReturn(fakeClientResource);
            when(fakeClientResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRoleMappingResource.clientLevel(eq(fakeClientRepresentationId)))
                    .thenReturn(fakeRoleScopeResource);

            keycloakAdapter.assignAdminRole(params);

            int expectedNumberOfRoleLookups = 4;
            int expectedNumberOfRoleAssignmentRequests = 2;

            verify(fakeRolesResource, times(expectedNumberOfRoleLookups)).get(anyString());
            verify(fakeRoleScopeResource, times(expectedNumberOfRoleAssignmentRequests))
                    .add(anyList());
        }
    }

    @Test
    void
            removeAdminRole_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.removeAdminRole(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void removeAdminRole_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.removeAdminRole(params));

            var expectedErrorMessage = "Something went wrong while removing admin role from user in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void removeAdminRole_shouldSucceedWhenNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeClientRepresentationId = UUID.randomUUID().toString();

        Map<String, Object> params = Map.of(USER_ID_MAP_KEY, fakeUserId, ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);
            RolesResource fakeRolesResource = mock(RolesResource.class);
            RoleResource fakeRoleResource = mock(RoleResource.class);
            RoleMappingResource fakeRoleMappingResource = mock(RoleMappingResource.class);
            RoleScopeResource fakeRoleScopeResource = mock(RoleScopeResource.class);
            ClientsResource fakeClientsResource = mock(ClientsResource.class);
            ClientResource fakeClientResource = mock(ClientResource.class);

            RoleRepresentation fakeUTBAdminRoleRepresentation = new RoleRepresentation();
            fakeUTBAdminRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeUTBAdminRoleRepresentation.setName(LH_USER_TASKS_ADMIN_ROLE);

            RoleRepresentation fakeViewRealmRoleRepresentation = new RoleRepresentation();
            fakeViewRealmRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeViewRealmRoleRepresentation.setName(VIEW_REALM_ROLE_NAME);

            RoleRepresentation fakeViewClientsRoleRepresentation = new RoleRepresentation();
            fakeViewClientsRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeViewClientsRoleRepresentation.setName(VIEW_CLIENTS_ROLE_NAME);

            RoleRepresentation fakeManageUsersRoleRepresentation = new RoleRepresentation();
            fakeManageUsersRoleRepresentation.setId(UUID.randomUUID().toString());
            fakeManageUsersRoleRepresentation.setName(MANAGE_USERS_ROLE_NAME);

            ClientRepresentation fakeClientRepresentation = new ClientRepresentation();
            fakeClientRepresentation.setId(fakeClientRepresentationId);
            fakeClientRepresentation.setClientId(REALM_MANAGEMENT_CLIENT_ID);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            when(fakeRealmResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRolesResource.get(anyString())).thenReturn(fakeRoleResource);
            when(fakeRoleResource.toRepresentation())
                    .thenReturn(
                            fakeUTBAdminRoleRepresentation,
                            fakeViewRealmRoleRepresentation,
                            fakeViewClientsRoleRepresentation,
                            fakeManageUsersRoleRepresentation);
            when(fakeUserResource.roles()).thenReturn(fakeRoleMappingResource);
            when(fakeRoleMappingResource.realmLevel()).thenReturn(fakeRoleScopeResource);
            when(fakeRealmResource.clients()).thenReturn(fakeClientsResource);
            when(fakeClientsResource.findByClientId(eq(REALM_MANAGEMENT_CLIENT_ID)))
                    .thenReturn(Collections.singletonList(fakeClientRepresentation));
            when(fakeClientsResource.get(eq(fakeClientRepresentationId))).thenReturn(fakeClientResource);
            when(fakeClientResource.roles()).thenReturn(fakeRolesResource);
            when(fakeRoleMappingResource.clientLevel(eq(fakeClientRepresentationId)))
                    .thenReturn(fakeRoleScopeResource);

            keycloakAdapter.removeAdminRole(params);

            int expectedNumberOfRoleLookups = 4;
            int expectedNumberOfRoleRemovalRequests = 2;

            verify(fakeRolesResource, times(expectedNumberOfRoleLookups)).get(anyString());
            verify(fakeRoleScopeResource, times(expectedNumberOfRoleRemovalRequests))
                    .remove(anyList());
        }
    }

    @Test
    void joinGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                "someGroupId");

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.joinGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void joinGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                "someGroupId");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.joinGroup(params));

            var expectedErrorMessage = "Something went wrong while joining user to a group in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void joinGroup_shouldThrowExceptionWhenNotFoundExceptionIsCaught() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeGroupId = "some-weird-group.id";

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                fakeGroupId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            doThrow(new NotFoundException()).when(fakeUserResource).joinGroup(eq(fakeGroupId));

            io.littlehorse.usertasks.exceptions.NotFoundException thrownException = assertThrows(
                    io.littlehorse.usertasks.exceptions.NotFoundException.class,
                    () -> keycloakAdapter.joinGroup(params));

            var expectedErrorMessage = "User or Group could not be found.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void joinGroup_shouldSucceedWhenNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var fakeGroupId = UUID.randomUUID().toString();

        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                fakeGroupId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.joinGroup(params);

            verify(fakeUserResource).joinGroup(eq(fakeGroupId));
        }
    }

    @Test
    void
            removeUserFromGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                "someGroupId");

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.removeUserFromGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void removeUserFromGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var fakeUserId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                "someGroupId");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.removeUserFromGroup(params));

            var expectedErrorMessage = "Something went wrong while removing user from group in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void removeUserFromGroup_shouldThrowExceptionWhenNotFoundExceptionIsCaught() {
        var fakeUserId = UUID.randomUUID().toString();
        var groupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                groupId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);
            doThrow(new NotFoundException()).when(fakeUserResource).leaveGroup(eq(groupId));

            io.littlehorse.usertasks.exceptions.NotFoundException thrownException = assertThrows(
                    io.littlehorse.usertasks.exceptions.NotFoundException.class,
                    () -> keycloakAdapter.removeUserFromGroup(params));

            var expectedErrorMessage = "User or Group could not be found.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void removeUserFromGroup_shouldSucceedWhenNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();
        var groupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(
                USER_ID_MAP_KEY,
                fakeUserId,
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                groupId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            UsersResource fakeUsersResource = mock(UsersResource.class);
            UserResource fakeUserResource = mock(UserResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(eq(fakeUserId))).thenReturn(fakeUserResource);

            keycloakAdapter.removeUserFromGroup(params);

            verify(fakeUserResource).leaveGroup(eq(groupId));
        }
    }

    @Test
    void
            createGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_NAME_MAP_KEY, "someGroup");

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void createGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_NAME_MAP_KEY, "someGroup");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createGroup(params));

            var expectedErrorMessage = "Something went wrong while creating a Group in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void createGroup_shouldThrowExceptionWhenResponseIsDifferentFromCreated() {
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_NAME_MAP_KEY, "someGroup");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.add(any(GroupRepresentation.class)))
                    .thenReturn(Response.serverError().build());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.createGroup(params));

            var expectedErrorMessage = "User creation failed within realm lh with status: 500!";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void createGroup_shouldSucceedWhenNoExceptionsAreThrownAndResponseIsCreated() throws URISyntaxException {
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_NAME_MAP_KEY, "someGroup");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);
            Response fakeResponse = Response.created(URI.create("www.some-fake-uri.com"))
                    .status(HttpStatus.CREATED.value())
                    .build();

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.add(any(GroupRepresentation.class))).thenReturn(fakeResponse);

            assertDoesNotThrow(() -> keycloakAdapter.createGroup(params));
        }
    }

    @Test
    void getGroups_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, FIRST_RESULT_MAP_KEY, 0, MAX_RESULTS_MAP_KEY, 10);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getGroups(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getGroups_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        Map<String, Object> params =
                Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, FIRST_RESULT_MAP_KEY, 0, MAX_RESULTS_MAP_KEY, 10);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.getGroups(params));

            var expectedErrorMessage = "Something went wrong while fetching Groups from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndNoUserGroupNameIsReceivedAndNoGroupsAreFound() {
        int firstResult = 0;
        int maxResults = 10;

        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                FIRST_RESULT_MAP_KEY,
                firstResult,
                MAX_RESULTS_MAP_KEY,
                maxResults);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups(eq(firstResult), eq(maxResults))).thenReturn(Collections.emptyList());

            Set<IDPGroupDTO> foundGroups = keycloakAdapter.getGroups(params);

            assertTrue(CollectionUtils.isEmpty(foundGroups));

            verify(fakeGroupsResource).groups(eq(firstResult), eq(maxResults));
        }
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndUserGroupNameIsReceivedAndNoGroupsAreFound() {
        int firstResult = 0;
        int maxResults = 10;
        var searchingGroupName = "my-group";

        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_NAME_MAP_KEY,
                searchingGroupName,
                FIRST_RESULT_MAP_KEY,
                firstResult,
                MAX_RESULTS_MAP_KEY,
                maxResults);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups(eq(searchingGroupName), eq(firstResult), eq(maxResults)))
                    .thenReturn(Collections.emptyList());

            Set<IDPGroupDTO> foundGroups = keycloakAdapter.getGroups(params);

            assertTrue(CollectionUtils.isEmpty(foundGroups));

            verify(fakeGroupsResource).groups(eq(searchingGroupName), eq(firstResult), eq(maxResults));
        }
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndNoUserGroupNameIsReceivedAndGroupsAreFound() {
        int firstResult = 0;
        int maxResults = 10;

        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                FIRST_RESULT_MAP_KEY,
                firstResult,
                MAX_RESULTS_MAP_KEY,
                maxResults);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);

            GroupRepresentation groupRepresentation1 = new GroupRepresentation();
            groupRepresentation1.setId(UUID.randomUUID().toString());
            groupRepresentation1.setName("my-group-1");

            GroupRepresentation groupRepresentation2 = new GroupRepresentation();
            groupRepresentation1.setId(UUID.randomUUID().toString());
            groupRepresentation1.setName("group-2");

            List<GroupRepresentation> foundGroupRepresentations = List.of(groupRepresentation1, groupRepresentation2);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups(eq(firstResult), eq(maxResults))).thenReturn(foundGroupRepresentations);

            Set<IDPGroupDTO> foundGroups = keycloakAdapter.getGroups(params);

            int expectedGroupsCount = 2;
            assertEquals(expectedGroupsCount, foundGroups.size());

            verify(fakeGroupsResource).groups(eq(firstResult), eq(maxResults));
        }
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndUserGroupNameIsReceivedAndGroupsAreFound() {
        int firstResult = 0;
        int maxResults = 10;
        var searchingGroupName = "my-group";

        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_NAME_MAP_KEY,
                searchingGroupName,
                FIRST_RESULT_MAP_KEY,
                firstResult,
                MAX_RESULTS_MAP_KEY,
                maxResults);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);

            GroupRepresentation groupRepresentation1 = new GroupRepresentation();
            groupRepresentation1.setId(UUID.randomUUID().toString());
            groupRepresentation1.setName("my-group-1");

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups(eq(searchingGroupName), eq(firstResult), eq(maxResults)))
                    .thenReturn(Collections.singletonList(groupRepresentation1));

            Set<IDPGroupDTO> foundGroups = keycloakAdapter.getGroups(params);

            int expectedGroupsCount = 1;
            assertEquals(expectedGroupsCount, foundGroups.size());

            verify(fakeGroupsResource).groups(eq(searchingGroupName), eq(firstResult), eq(maxResults));
        }
    }

    @Test
    void
            updateGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        String groupId = UUID.randomUUID().toString();
        String groupName = "some-group";
        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                groupId,
                USER_GROUP_NAME_MAP_KEY,
                groupName);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.updateGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void updateGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        String groupId = UUID.randomUUID().toString();
        String groupName = "some-group";
        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                groupId,
                USER_GROUP_NAME_MAP_KEY,
                groupName);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.updateGroup(params));

            var expectedErrorMessage = "Something went wrong while renaming a Group in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void updateGroup_shouldSucceedWhenNoExceptionsAreThrown() {
        String groupId = UUID.randomUUID().toString();
        String groupName = "some-group";
        Map<String, Object> params = Map.of(
                ACCESS_TOKEN_MAP_KEY,
                STUBBED_ACCESS_TOKEN,
                USER_GROUP_ID_MAP_KEY,
                groupId,
                USER_GROUP_NAME_MAP_KEY,
                groupName);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);
            GroupResource fakeGroupResource = mock(GroupResource.class);

            GroupRepresentation groupRepresentation = new GroupRepresentation();
            groupRepresentation.setId(groupId);
            groupRepresentation.setName("some-other-name");

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.group(eq(groupId))).thenReturn(fakeGroupResource);
            when(fakeGroupResource.toRepresentation()).thenReturn(groupRepresentation);

            keycloakAdapter.updateGroup(params);

            ArgumentCaptor<GroupRepresentation> argumentCaptor = ArgumentCaptor.forClass(GroupRepresentation.class);

            verify(fakeGroupResource).update(argumentCaptor.capture());

            GroupRepresentation updateGroupRepresentation = argumentCaptor.getValue();

            assertTrue(StringUtils.equalsIgnoreCase(groupId, updateGroupRepresentation.getId()));
            assertTrue(StringUtils.equalsIgnoreCase(groupName, updateGroupRepresentation.getName()));
        }
    }

    @Test
    void
            deleteGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_ID_MAP_KEY, groupId);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.deleteGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void deleteGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_ID_MAP_KEY, groupId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException =
                    assertThrows(AdapterException.class, () -> keycloakAdapter.deleteGroup(params));

            var expectedErrorMessage = "Something went wrong while deleting a Group in Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void deleteGroup_shouldSucceedWhenNoExceptionsAreThrown() {
        String groupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of(ACCESS_TOKEN_MAP_KEY, STUBBED_ACCESS_TOKEN, USER_GROUP_ID_MAP_KEY, groupId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            RealmResource fakeRealmResource = mock(RealmResource.class);
            GroupsResource fakeGroupsResource = mock(GroupsResource.class);
            GroupResource fakeGroupResource = mock(GroupResource.class);

            mockStaticKeycloak
                    .when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.group(eq(groupId))).thenReturn(fakeGroupResource);

            keycloakAdapter.deleteGroup(params);

            verify(fakeGroupResource).remove();
        }
    }

    private UserRepresentation getFakeUserRepresentation(String username) {
        UserRepresentation userRepresentation = new UserRepresentation();
        userRepresentation.setId(UUID.randomUUID().toString());
        userRepresentation.setUsername(username);
        userRepresentation.setEnabled(true);

        return userRepresentation;
    }
}
