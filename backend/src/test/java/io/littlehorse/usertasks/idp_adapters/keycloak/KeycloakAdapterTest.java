package io.littlehorse.usertasks.idp_adapters.keycloak;

import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.responses.UserGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;
import jakarta.ws.rs.NotFoundException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
import org.keycloak.admin.client.resource.GroupResource;
import org.keycloak.admin.client.resource.GroupsResource;
import org.keycloak.admin.client.resource.RealmResource;
import org.keycloak.admin.client.resource.UserResource;
import org.keycloak.admin.client.resource.UsersResource;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.mockito.MockedStatic;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class KeycloakAdapterTest {
    private final KeycloakAdapter keycloakAdapter = new KeycloakAdapter();

    private final String STUBBED_ACCESS_TOKEN = "eyJhbGciOiJSUzI1NiIsInR5cCIgOiAiSldUIiwia2lkIiA6ICIxemZidUhsMWtZX3k4bTFFaXFwdHVFNFNrbG1CNW" +
            "JlaFpVN1ZVeG5TMzlBIn0.eyJleHAiOjE3MjE4NTU5ODQsImlhdCI6MTcyMTg1NTY4NCwianRpIjoiZGM4ZjVhOWUtOGRkZS00ZDY4LThmN" +
            "GYtYTc5NzRhYzgwODgxIiwiaXNzIjoiaHR0cDovL2xvY2FsaG9zdDo4ODg4L3JlYWxtcy9saCIsImF1ZCI6WyJyZWFsbS1tYW5hZ2VtZW50" +
            "IiwiYWNjb3VudCJdLCJzdWIiOiJlNWRmYmIwOC1lMzA4LTQwYjEtYmI5YS01YTI0NWQ1OTRiYzIiLCJ0eXAiOiJCZWFyZXIiLCJhenAiOiJ" +
            "1c2VyLXRhc2tzLWNsaWVudCIsInNlc3Npb25fc3RhdGUiOiJjZWE4NThiOC1mOTUwLTQzMmEtYWRlNi05MmJjNzIwMzRlNjIiLCJhY3IiOi" +
            "IxIiwicmVhbG1fYWNjZXNzIjp7InJvbGVzIjpbImxoLXVzZXItdGFza3MtYWRtaW4iLCJvZmZsaW5lX2FjY2VzcyIsImRlZmF1bHQtcm9sZ" +
            "XMtbGgiLCJ1bWFfYXV0aG9yaXphdGlvbiJdfSwicmVzb3VyY2VfYWNjZXNzIjp7InJlYWxtLW1hbmFnZW1lbnQiOnsicm9sZXMiOlsidmll" +
            "dy11c2VycyIsInF1ZXJ5LWdyb3VwcyIsInF1ZXJ5LXVzZXJzIl19LCJhY2NvdW50Ijp7InJvbGVzIjpbIm1hbmFnZS1hY2NvdW50Iiwidml" +
            "ldy1ncm91cHMiLCJtYW5hZ2UtYWNjb3VudC1saW5rcyIsInZpZXctcHJvZmlsZSJdfX0sInNjb3BlIjoiZW1haWwgcHJvZmlsZSIsInNpZC" +
            "I6ImNlYTg1OGI4LWY5NTAtNDMyYS1hZGU2LTkyYmM3MjAzNGU2MiIsImVtYWlsX3ZlcmlmaWVkIjpmYWxzZSwicHJlZmVycmVkX3VzZXJuY" +
            "W1lIjoibGgtYWRtaW4tdXNlciIsImdpdmVuX25hbWUiOiIiLCJmYW1pbHlfbmFtZSI6IiJ9.pTNWob_yMM5ECshFjum1wJ4P2NcBB6edsYB" +
            "YKS6DQknx3HWRRgx0Sfi6gZiL_V9ehWk3O96ogzkA5SXyyfKrDf3Hbjj9om4eit9pXR1679toq4BTxUDvh46ERad1JzKHLo-sowzJB5o3by" +
            "0kG4P5t3VwdaprlKgi6OhJbs8c2VMTjbFF_l5SeMlVno0wyMFGEDXQU6ENPgY7Jk29esTiizNmdigoxOgpK0wmDh4NJqe4TajO0cOKvkT1q" +
            "p-0GFtpPXwUXn6Ghdsx_4Fwgox3SwDQdJUuit-UlTVjbjWyDgdWgB28Tn08K6MY-Yht_vPfqj1wgc7aArRDWjvZBGXRBA";

    private final Map<String, Object> standardParams = Map.of("accessToken", STUBBED_ACCESS_TOKEN);

    @Test
    void getUserGroups_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUserGroups(standardParams));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroups_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUserGroups(standardParams));

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(fakeGroups);

            UserGroupListDTO foundUserGroups = keycloakAdapter.getUserGroups(standardParams);

            int expectedQuantityOfGroups = 2;

            assertNotNull(foundUserGroups);
            assertFalse(foundUserGroups.getGroups().isEmpty());
            assertTrue(foundUserGroups.getGroups().stream().allMatch(userGroupDTO -> StringUtils.isNotBlank(userGroupDTO.getName())));
            assertEquals(expectedQuantityOfGroups, foundUserGroups.getGroups().size());
        }
    }

    @Test
    void getUserGroups_shouldReturnEmptySetOfUserGroupNamesWhenNoGroupsExist() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
    void getMyUserGroups_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getMyUserGroups(standardParams));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getMyUserGroups_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getMyUserGroups(standardParams));

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            UserGroupListDTO foundUserGroups = keycloakAdapter.getMyUserGroups(standardParams);

            int expectedQuantityOfGroups = 1;

            assertNotNull(foundUserGroups);
            assertFalse(foundUserGroups.getGroups().isEmpty());
            assertTrue(foundUserGroups.getGroups().stream().allMatch(userGroupDTO -> StringUtils.isNotBlank(userGroupDTO.getName())));
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUsers(params));

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUsers(params));

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUsers(params));

            var expectedErrorMessage = "Combination of userGroup + other filters (username/email/firstName/lastName) is not supported";

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(eq(randomUsernamePrefix), eq(null), eq(null), eq(null), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getUsername())));
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(eq(null), eq(randomFirstNamePrefix), eq(null), eq(null), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 3;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getFirstName())));
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(eq(null), eq(null), eq(randomLastNamePrefix), eq(null), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 2;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getLastName())));
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.search(eq(null), eq(null), eq(null), eq(randomEmailAddress), anyInt(), anyInt(),
                    anyBoolean(), anyBoolean(), anyBoolean())).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            int expectedQuantityOfUsers = 1;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream()
                    .allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId()) && StringUtils.isNotBlank(userDTO.getEmail())));
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.list()).thenReturn(Collections.emptyList());

            UserListDTO foundUsers = keycloakAdapter.getUsers(params);

            assertTrue(foundUsers.getUsers().isEmpty());
        }
    }

    @Test
    void validateUserGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.validateUserGroup("someGroup", STUBBED_ACCESS_TOKEN));

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(Collections.emptyList());

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            assertDoesNotThrow(() -> keycloakAdapter.validateUserGroup(idGroup2, STUBBED_ACCESS_TOKEN));
        }
    }

    @Test
    void getUserInfo_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var userId = "someUserId";
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUserInfo(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserInfo_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var userId = "someUserId";
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUserInfo(params));

            var expectedErrorMessage = "Something went wrong while fetching User's info from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserInfo_shouldReturnUserDTOWhenUserRepresentationIsFoundUsingUserId() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN,
                "email", "someaddress@somedomain.com");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByEmail(anyString(), anyBoolean())).thenReturn(List.of(fakeUserRepresentation));

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNotNull(foundUserInfo);
            assertTrue(StringUtils.isNotBlank(foundUserInfo.getId()));
        }
    }

    @Test
    void getUserInfo_shouldReturnNullWhenUserRepresentationIsNotFoundUsingEmail() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN,
                "email", "someweirdaddress@somedomain.co");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN,
                "username", "my-username");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByUsername(anyString(), anyBoolean())).thenReturn(List.of(fakeUserRepresentation));

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNotNull(foundUserInfo);
            assertTrue(StringUtils.isNotBlank(foundUserInfo.getId()));
        }
    }

    @Test
    void getUserInfo_shouldReturnNullWhenUserRepresentationIsNotFoundUsingUsername() {
        String userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN,
                "username", "my-username");

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.searchByUsername(anyString(), anyBoolean())).thenReturn(Collections.emptyList());

            UserDTO foundUserInfo = keycloakAdapter.getUserInfo(params);

            assertNull(foundUserInfo);
        }
    }

    @Test
    void getUserGroup_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userGroupId", userGroupId, "accessToken", STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUserGroup(params));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroup_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userGroupId", userGroupId, "accessToken", STUBBED_ACCESS_TOKEN);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenThrow(new RuntimeException());

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUserGroup(params));

            var expectedErrorMessage = "Something went wrong while fetching Group's info from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUserGroup_shouldReturnNullWhenUserGroupRepresentationIsNotFound() {
        var userGroupId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userGroupName", userGroupId, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userGroupName", userGroupNameParam, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userGroupName", userGroupNameParam, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);
        GroupRepresentation fakeGroupRepresentation = new GroupRepresentation();
        fakeGroupRepresentation.setId(UUID.randomUUID().toString());
        fakeGroupRepresentation.setName("some-group-name");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userGroupName", "some-group-name", "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);
        GroupRepresentation fakeGroupRepresentation = new GroupRepresentation();
        fakeGroupRepresentation.setId(userGroupId);
        fakeGroupRepresentation.setName("some-group-name");

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> keycloakAdapter.validateAssignmentProperties(Collections.emptyMap()));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
        var expectedErrorMessage = "No valid arguments were received to complete reassignment.";

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals(expectedErrorMessage, thrownException.getReason());
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenNoUserAndUserGroupAreProvided() {
        Map<String, Object> params = Map.of("someKey", new Object());

        ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                () -> keycloakAdapter.validateAssignmentProperties(params));

        int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
        var expectedErrorMessage = "No valid arguments were received to complete reassignment.";

        assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
        assertEquals(expectedErrorMessage, thrownException.getReason());
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdIsReceivedButNoUserInfoIsFound() {
        var userId = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenThrow(new NotFoundException());

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                    () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent user.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserGroupIsReceivedButItIsNotFoundWithinRealm() {
        var requestedUserGroup = "my-group";
        Map<String, Object> params = Map.of("userGroupId", requestedUserGroup, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(Collections.emptyList());

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                    () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent group, nor can the Task be assigned to an " +
                    "existing group that the requested user is not a member of.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserGroupIsReceivedButItIsNotPartOfTheOnesInRealm() {
        var requestedUserGroup = "my-group";
        Map<String, Object> params = Map.of("userGroupId", requestedUserGroup, "accessToken", STUBBED_ACCESS_TOKEN);

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(fakeGroups);

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                    () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent group, nor can the Task be assigned to an " +
                    "existing group that the requested user is not a member of.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdAndUserGroupAreReceivedButUserGroupIsNotFoundWithinRealm() {
        var userId = UUID.randomUUID().toString();
        var requestedUserGroup = "my-group";
        Map<String, Object> params = Map.of("userId", userId, "userGroupId", requestedUserGroup, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        GroupsResource fakeGroupsResource = mock(GroupsResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(fakeUserRepresentation);

            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(Collections.emptyList());

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                    () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent group, nor can the Task be assigned to an " +
                    "existing group that the requested user is not a member of.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void validateAssignmentProperties_shouldThrowResponseStatusExceptionAsBadRequestWhenUserIdAndUserGroupAreReceivedButNoUserInfoIsFound() {
        var userId = UUID.randomUUID().toString();
        var requestedUserGroup = "my-group";
        Map<String, Object> params = Map.of("userId", userId, "userGroupId", requestedUserGroup, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenThrow(new NotFoundException());

            ResponseStatusException thrownException = assertThrows(ResponseStatusException.class,
                    () -> keycloakAdapter.validateAssignmentProperties(params));

            int expectedHttpErrorCode = HttpStatus.BAD_REQUEST.value();
            var expectedErrorMessage = "Cannot assign Task to non-existent user.";

            assertEquals(expectedHttpErrorCode, thrownException.getBody().getStatus());
            assertEquals(expectedErrorMessage, thrownException.getReason());
        }
    }

    @Test
    void validateAssignmentProperties_shouldNotThrowAnyExceptionWhenUserIdAndUserGroupAreReceivedAndTheyAreFoundWithinRealm() {
        var userId = UUID.randomUUID().toString();
        var requestedUserGroup = UUID.randomUUID().toString();
        Map<String, Object> params = Map.of("userId", userId, "userGroupId", requestedUserGroup, "accessToken", STUBBED_ACCESS_TOKEN);

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userGroupId", requestedUserGroup, "accessToken", STUBBED_ACCESS_TOKEN);

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
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
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
        Map<String, Object> params = Map.of("userId", userId, "accessToken", STUBBED_ACCESS_TOKEN);

        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);
        UserResource fakeUserResource = mock(UserResource.class);
        UserRepresentation fakeUserRepresentation = new UserRepresentation();
        fakeUserRepresentation.setId(userId);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.toRepresentation()).thenReturn(fakeUserRepresentation);

            assertDoesNotThrow(() -> keycloakAdapter.validateAssignmentProperties(params));
        }
    }
}
