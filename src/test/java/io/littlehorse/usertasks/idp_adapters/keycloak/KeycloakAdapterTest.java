package io.littlehorse.usertasks.idp_adapters.keycloak;

import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.keycloak.admin.client.Keycloak;
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
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

class KeycloakAdapterTest {
    private final KeycloakAdapter keycloakAdapter = new KeycloakAdapter();

    private final String FAKE_REALM = "someRealm";
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

    private final Map<String, Object> standardParams = Map.of("realm", FAKE_REALM, "accessToken", STUBBED_ACCESS_TOKEN);

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
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenThrow(new RuntimeException());

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
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(fakeGroups);

            Set<String> foundUserGroups = keycloakAdapter.getUserGroups(standardParams);

            int expectedQuantityOfGroups = 2;

            assertFalse(foundUserGroups.isEmpty());
            assertTrue(foundUserGroups.stream().allMatch(StringUtils::isNotBlank));
            assertEquals(expectedQuantityOfGroups, foundUserGroups.size());
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
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenReturn(fakeRealmResource);
            when(fakeRealmResource.groups()).thenReturn(fakeGroupsResource);
            when(fakeGroupsResource.groups()).thenReturn(Collections.emptyList());

            Set<String> foundUserGroups = keycloakAdapter.getUserGroups(standardParams);

            assertTrue(foundUserGroups.isEmpty());
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
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenThrow(new RuntimeException());

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
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(fakeGroups);

            Set<String> foundUserGroups = keycloakAdapter.getMyUserGroups(standardParams);

            int expectedQuantityOfGroups = 1;

            assertFalse(foundUserGroups.isEmpty());
            assertTrue(foundUserGroups.stream().allMatch(StringUtils::isNotBlank));
            assertEquals(expectedQuantityOfGroups, foundUserGroups.size());
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
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.get(anyString())).thenReturn(fakeUserResource);
            when(fakeUserResource.groups()).thenReturn(Collections.emptyList());


            Set<String> foundUserGroups = keycloakAdapter.getMyUserGroups(standardParams);

            assertTrue(foundUserGroups.isEmpty());
        }
    }

    @Test
    void getUsers_shouldThrowAdapterExceptionCreatingKeycloakInstanceWhenRuntimeExceptionIsThrownGettingNewInstance() {
        try (MockedStatic<Keycloak> ignored = mockStatic(Keycloak.class)) {
            when(Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenThrow(new RuntimeException("Error"));

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUsers(standardParams));

            var expectedErrorMessage = "Something went wrong while creating Keycloak instance.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUsers_shouldThrowExceptionCreatingKeycloakInstanceWhenAccessingRealms() {
        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(FAKE_REALM)).thenThrow(new RuntimeException());

            AdapterException thrownException = assertThrows(AdapterException.class,
                    () -> keycloakAdapter.getUsers(standardParams));

            var expectedErrorMessage = "Something went wrong while fetching all Users from Keycloak realm.";

            assertEquals(expectedErrorMessage, thrownException.getMessage());
        }
    }

    @Test
    void getUsers_shouldReturnSetOfUsernamesWhenNoExceptionIsThrown() {
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
            when(fakeUsersResource.list()).thenReturn(fakeUsers);

            UserListDTO foundUsers = keycloakAdapter.getUsers(standardParams);

            int expectedQuantityOfUsers = 3;

            assertFalse(foundUsers.getUsers().isEmpty());
            assertTrue(foundUsers.getUsers().stream().allMatch(userDTO -> StringUtils.isNotBlank(userDTO.getId())));
            assertEquals(expectedQuantityOfUsers, foundUsers.getUsers().size());
        }
    }

    @Test
    void getUsers_shouldReturnEmptySetOfUsernamesWhenNoGroupsExist() {
        RealmResource fakeRealmResource = mock(RealmResource.class);
        UsersResource fakeUsersResource = mock(UsersResource.class);

        try (MockedStatic<Keycloak> mockStaticKeycloak = mockStatic(Keycloak.class)) {
            Keycloak mockKeycloakInstance = mock(Keycloak.class);
            mockStaticKeycloak.when(() -> Keycloak.getInstance(anyString(), anyString(), anyString(), anyString()))
                    .thenReturn(mockKeycloakInstance);
            when(mockKeycloakInstance.realm(anyString())).thenReturn(fakeRealmResource);
            when(fakeRealmResource.users()).thenReturn(fakeUsersResource);
            when(fakeUsersResource.list()).thenReturn(Collections.emptyList());

            UserListDTO foundUsers = keycloakAdapter.getUsers(standardParams);

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

            assertDoesNotThrow(() -> keycloakAdapter.validateUserGroup("Group #2", STUBBED_ACCESS_TOKEN));
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
    void getUserInfo_shouldReturnUserDTOWhenUserRepresentationIsFound() {
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
}
