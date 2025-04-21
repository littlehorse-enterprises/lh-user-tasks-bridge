package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.exceptions.AdapterException;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.CreateManagedUserRequest;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.requests.UpdateManagedUserRequest;
import io.littlehorse.usertasks.models.requests.UpsertPasswordRequest;
import io.littlehorse.usertasks.models.responses.IDPGroupDTO;
import io.littlehorse.usertasks.models.responses.IDPUserDTO;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class UserManagementServiceTest {
    private final UserManagementService userManagementService = new UserManagementService();

    private final IStandardIdentityProviderAdapter keycloakAdapter = mock(KeycloakAdapter.class);

    String fakeAccessToken = "some-fake-access-token";
    int firstResult = 0;
    int maxResult = 10;

    @Test
    void listUsersFromIdentityProvider_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder().build();

        assertThrows(NullPointerException.class,
                () -> userManagementService.listUsersFromIdentityProvider(null, keycloakAdapter, requestFilter, firstResult, maxResult));

        verify(keycloakAdapter, never()).getManagedUsers(anyMap());
    }

    @Test
    void listUsersFromIdentityProvider_shouldThrowNullPointerExceptionWhenNullRequestFilterIsReceived() {
        assertThrows(NullPointerException.class,
                () -> userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, null, firstResult, maxResult));

        verify(keycloakAdapter, never()).getManagedUsers(anyMap());
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnEmptyListWhenNoUsersAreFoundWithNoFiltersUsingKeycloak() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder().build();

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(new IDPUserListDTO());

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        assertNotNull(listOfUsers);
        assertTrue(CollectionUtils.isEmpty(listOfUsers.getUsers()));

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 3;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnListOfUsersWhenUsersAreFoundWithNoFiltersUsingKeycloak() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder().build();

        IDPUserListDTO foundUsersList = new IDPUserListDTO(Set.of(fakeUserSupplier().get(), fakeUserSupplier().get(), fakeUserSupplier().get()));

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(foundUsersList);

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        int expectedUsersCount = 3;

        assertNotNull(listOfUsers);
        assertFalse(CollectionUtils.isEmpty(listOfUsers.getUsers()));
        assertEquals(expectedUsersCount, foundUsersList.getUsers().size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 3;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnListOfUsersWhenUsersAreFoundFilteredByEmailUsingKeycloak() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder()
                .email("somedomain.com")
                .build();

        IDPUserDTO user1 = fakeUserSupplier().get();
        IDPUserDTO user2 = fakeUserSupplier().get();

        user1.setEmail("someemail@somedomain.com");
        user2.setEmail("someotheremail@somedomain.com");

        IDPUserListDTO foundUsersList = new IDPUserListDTO(Set.of(user1, user2));

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(foundUsersList);

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        int expectedUsersCount = 2;

        assertNotNull(listOfUsers);
        assertFalse(CollectionUtils.isEmpty(listOfUsers.getUsers()));
        assertEquals(expectedUsersCount, foundUsersList.getUsers().size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 4;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
        assertEquals(expectedParamsCount, paramsUsedToSearchUsers.size());
        assertTrue(paramsUsedToSearchUsers.containsKey("email"));
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnListOfUsersWhenUsersAreFoundFilteredByFirstNameUsingKeycloak() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder()
                .firstName("Mar")
                .build();

        IDPUserDTO user1 = fakeUserSupplier().get();
        IDPUserDTO user2 = fakeUserSupplier().get();
        IDPUserDTO user3 = fakeUserSupplier().get();

        user1.setFirstName("Martha");
        user2.setFirstName("Marlon");
        user3.setFirstName("Margarita");

        IDPUserListDTO foundUsersList = new IDPUserListDTO(Set.of(user1, user2, user3));

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(foundUsersList);

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        int expectedUsersCount = 3;

        assertNotNull(listOfUsers);
        assertFalse(CollectionUtils.isEmpty(listOfUsers.getUsers()));
        assertEquals(expectedUsersCount, foundUsersList.getUsers().size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 4;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
        assertTrue(paramsUsedToSearchUsers.containsKey("firstName"));
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnListOfUsersWhenUsersAreFoundFilteredByLastNameUsingKeycloak() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder()
                .lastName("th")
                .build();

        IDPUserDTO user1 = fakeUserSupplier().get();
        IDPUserDTO user2 = fakeUserSupplier().get();
        IDPUserDTO user3 = fakeUserSupplier().get();

        user1.setLastName("Smith");
        user2.setLastName("Roth");
        user3.setLastName("Brotherton");

        IDPUserListDTO foundUsersList = new IDPUserListDTO(Set.of(user1, user2, user3));

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(foundUsersList);

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        int expectedUsersCount = 3;

        assertNotNull(listOfUsers);
        assertFalse(CollectionUtils.isEmpty(listOfUsers.getUsers()));
        assertEquals(expectedUsersCount, foundUsersList.getUsers().size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 4;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
        assertTrue(paramsUsedToSearchUsers.containsKey("lastName"));
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnListOfUsersWhenUsersAreFoundFilteredByUsernameUsingKeycloak() {
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder()
                .username("any")
                .build();

        IDPUserDTO user1 = fakeUserSupplier().get();
        IDPUserDTO user2 = fakeUserSupplier().get();

        user1.setUsername("Brittany");
        user2.setUsername("Stephany");

        IDPUserListDTO foundUsersList = new IDPUserListDTO(Set.of(user1, user2));

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(foundUsersList);

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        int expectedUsersCount = 2;

        assertNotNull(listOfUsers);
        assertFalse(CollectionUtils.isEmpty(listOfUsers.getUsers()));
        assertEquals(expectedUsersCount, foundUsersList.getUsers().size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 4;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
        assertTrue(paramsUsedToSearchUsers.containsKey("username"));
    }

    @Test
    void listUsersFromIdentityProvider_shouldReturnListOfUsersWhenUsersAreFoundFilteredByUserGroupIdUsingKeycloak() {
        String userGroupId = UUID.randomUUID().toString();
        IDPUserSearchRequestFilter requestFilter = IDPUserSearchRequestFilter.builder()
                .userGroupId(userGroupId)
                .build();

        IDPUserDTO user1 = fakeUserSupplier().get();
        IDPUserDTO user2 = fakeUserSupplier().get();
        IDPUserDTO user3 = fakeUserSupplier().get();

        user1.getGroups().iterator().next().setId(userGroupId);
        user2.getGroups().iterator().next().setId(userGroupId);
        user3.getGroups().iterator().next().setId(userGroupId);

        IDPUserListDTO foundUsersList = new IDPUserListDTO(Set.of(user1, user2, user3));

        when(keycloakAdapter.getManagedUsers(anyMap())).thenReturn(foundUsersList);

        IDPUserListDTO listOfUsers = userManagementService.listUsersFromIdentityProvider(fakeAccessToken, keycloakAdapter, requestFilter, firstResult, maxResult);

        int expectedUsersCount = 3;

        assertNotNull(listOfUsers);
        assertFalse(CollectionUtils.isEmpty(listOfUsers.getUsers()));
        assertEquals(expectedUsersCount, foundUsersList.getUsers().size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUsers(argumentCaptor.capture());

        Map<String, Object> paramsUsedToSearchUsers = argumentCaptor.getValue();
        int expectedParamsCount = 4;

        assertMandatoryParamsForKeycloakSearch(paramsUsedToSearchUsers, expectedParamsCount);
        assertTrue(paramsUsedToSearchUsers.containsKey("userGroupId"));
    }

    @Test
    void createUserInIdentityProvider_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        CreateManagedUserRequest request = CreateManagedUserRequest.builder().build();
        assertThrows(NullPointerException.class,
                ()-> userManagementService.createUserInIdentityProvider(null, request, keycloakAdapter));
    }

    @Test
    void createUserInIdentityProvider_shouldThrowNullPointerExceptionWhenNullRequestObjectIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> userManagementService.createUserInIdentityProvider(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void createUserInIdentityProvider_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        CreateManagedUserRequest request = CreateManagedUserRequest.builder().build();
        assertThrows(NullPointerException.class,
                ()-> userManagementService.createUserInIdentityProvider(fakeAccessToken, request, null));
    }

    @Test
    void createUserInIdentityProvider_shouldSucceedWhenNoExceptionsAreThrownUsingKeycloak() {
        var fakeFirstName = "myFirstName";
        var fakeLastName = "myLastName";
        var fakeUsername = "myUsername";
        var fakeEmail = "myemail@somedomain.com";

        CreateManagedUserRequest request = CreateManagedUserRequest.builder()
                .firstName(fakeFirstName)
                .lastName(fakeLastName)
                .username(fakeUsername)
                .email(fakeEmail)
                .build();

        userManagementService.createUserInIdentityProvider(fakeAccessToken, request, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).createManagedUser(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();
        int expectedParamsCount = 5;

        assertFalse(paramsSent.isEmpty());
        assertEquals(expectedParamsCount, paramsSent.size());
        assertEquals(fakeAccessToken, paramsSent.get("accessToken"));
        assertEquals(fakeFirstName, paramsSent.get("firstName"));
        assertEquals(fakeLastName, paramsSent.get("lastName"));
        assertEquals(fakeUsername, paramsSent.get("username"));
        assertEquals(fakeEmail, paramsSent.get("email"));
    }

    @Test
    void setPassword_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived(){
        UpsertPasswordRequest request = UpsertPasswordRequest.builder().build();
        assertThrows(NullPointerException.class,
                ()-> userManagementService.setPassword(null, "someUserId", request, keycloakAdapter));
    }

    @Test
    void setPassword_shouldThrowNullPointerExceptionWhenNullUserIdIsReceived(){
        UpsertPasswordRequest request = UpsertPasswordRequest.builder().build();
        assertThrows(NullPointerException.class,
                ()-> userManagementService.setPassword(fakeAccessToken, null, request, keycloakAdapter));
    }

    @Test
    void setPassword_shouldThrowNullPointerExceptionWhenNullRequestIsReceived(){
        assertThrows(NullPointerException.class,
                ()-> userManagementService.setPassword(fakeAccessToken, "someUserId", null, keycloakAdapter));
    }

    @Test
    void setPassword_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived(){
        UpsertPasswordRequest request = UpsertPasswordRequest.builder().build();
        assertThrows(NullPointerException.class,
                ()-> userManagementService.setPassword(fakeAccessToken, "someUserId", request, null));
    }

    @Test
    void setPassword_shouldSucceedWhenNoExceptionsAreThrown() {
        var fakeUserId = "someUserId";
        var fakePassword = "my-nice-password";
        UpsertPasswordRequest request = UpsertPasswordRequest.builder()
                .password(fakePassword)
                .temporary(false)
                .build();

        userManagementService.setPassword(fakeAccessToken, fakeUserId, request, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).setPassword(eq(fakeUserId), argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 3;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertEquals(fakePassword, paramsSent.get("password"));
        assertFalse((boolean)paramsSent.get("isTemporary"));
    }

    @Test
    void getUserFromIdentityProvider_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        assertThrows(NullPointerException.class,
                () -> userManagementService.getUserFromIdentityProvider(null, UUID.randomUUID().toString(), keycloakAdapter));
    }

    @Test
    void getUserFromIdentityProvider_shouldThrowNullPointerExceptionWhenNullUserIdIsReceived() {
        assertThrows(NullPointerException.class,
                () -> userManagementService.getUserFromIdentityProvider(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void getUserFromIdentityProvider_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        assertThrows(NullPointerException.class,
                () -> userManagementService.getUserFromIdentityProvider(fakeAccessToken, UUID.randomUUID().toString(), null));
    }

    @Test
    void getUserFromIdentityProvider_shouldSucceedWhenNoExceptionsAreThrownAndDataIsFound() {
        String userId = UUID.randomUUID().toString();
        IDPUserDTO userDTO = IDPUserDTO.builder()
                .id(userId)
                .username("my-cool-username")
                .email("myemail@somedomain.com")
                .realmRoles(Set.of("myRole"))
                .build();

        when(keycloakAdapter.getManagedUser(anyMap())).thenReturn(userDTO);

        Optional<IDPUserDTO> optionalUserDTO = userManagementService.getUserFromIdentityProvider(fakeAccessToken, userId, keycloakAdapter);

        assertTrue(optionalUserDTO.isPresent());
        assertTrue(StringUtils.equalsIgnoreCase(userId, optionalUserDTO.get().getId()));

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUser(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(userId, (String) paramsSent.get("userId")));
    }

    @Test
    void getUserFromIdentityProvider_shouldSucceedWhenNoExceptionsAreThrownAndNoDataIsFound() {
        String userId = UUID.randomUUID().toString();

        when(keycloakAdapter.getManagedUser(anyMap())).thenReturn(null);

        Optional<IDPUserDTO> optionalUserDTO = userManagementService.getUserFromIdentityProvider(fakeAccessToken, userId, keycloakAdapter);

        assertFalse(optionalUserDTO.isPresent());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getManagedUser(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(userId, (String) paramsSent.get("userId")));
    }

    @Test
    void updateUser_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        var userId = UUID.randomUUID().toString();
        UpdateManagedUserRequest request = UpdateManagedUserRequest.builder().build();

        assertThrows(NullPointerException.class,
                ()-> userManagementService.updateUser(null, userId, request, keycloakAdapter));
    }

    @Test
    void updateUser_shouldThrowNullPointerExceptionWhenNullUserIdIsReceived() {
        UpdateManagedUserRequest request = UpdateManagedUserRequest.builder().build();

        assertThrows(NullPointerException.class,
                ()-> userManagementService.updateUser(fakeAccessToken, null, request, keycloakAdapter));
    }

    @Test
    void updateUser_shouldThrowNullPointerExceptionWhenNullRequestIsReceived() {
        var userId = UUID.randomUUID().toString();

        assertThrows(NullPointerException.class,
                ()-> userManagementService.updateUser(fakeAccessToken, userId, null, keycloakAdapter));
    }

    @Test
    void updateUser_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        var userId = UUID.randomUUID().toString();
        UpdateManagedUserRequest request = UpdateManagedUserRequest.builder().build();

        assertThrows(NullPointerException.class,
                ()-> userManagementService.updateUser(fakeAccessToken, userId, request, null));
    }

    @Test
    void updateUser_shouldSucceedWhenNoExceptionIsThrownAndNoUsernameIsProvided() {
        var userId = UUID.randomUUID().toString();
        var fakeEmail = "somerandommail@somedomain.com";
        var fakeFirstName = "someName";
        var fakeLastName = "someLastName";

        UpdateManagedUserRequest request = UpdateManagedUserRequest.builder()
                .enabled(true)
                .email(fakeEmail)
                .firstName(fakeFirstName)
                .lastName(fakeLastName)
                .build();

        userManagementService.updateUser(fakeAccessToken, userId, request, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).updateManagedUser(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 6;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertEquals(userId, paramsSent.get("userId"));
        assertEquals(fakeAccessToken, paramsSent.get("accessToken"));
        assertEquals(true, paramsSent.get("enabled"));
        assertNull(paramsSent.get("username"));
        assertEquals(fakeFirstName, paramsSent.get("firstName"));
        assertEquals(fakeLastName, paramsSent.get("lastName"));
        assertEquals(fakeEmail, paramsSent.get("email"));
    }

    @Test
    void updateUser_shouldThrowExceptionWhenUsernameIsProvidedAndKeycloakAdapterThrowsAdapterException() {
        var userId = UUID.randomUUID().toString();
        var fakeEmail = "somerandommail@somedomain.com";
        var fakeFirstName = "someName";
        var fakeLastName = "someLastName";
        var fakeUsername = "someUsername";

        UpdateManagedUserRequest request = UpdateManagedUserRequest.builder()
                .enabled(true)
                .email(fakeEmail)
                .firstName(fakeFirstName)
                .lastName(fakeLastName)
                .username(fakeUsername)
                .build();

        var exceptionMessage = "Username is not allowed to be edited!";

        doThrow(new AdapterException(exceptionMessage)).when(keycloakAdapter).updateManagedUser(anyMap());

        AdapterException expectedException = assertThrows(AdapterException.class,
                () -> userManagementService.updateUser(fakeAccessToken, userId, request, keycloakAdapter));

        assertTrue(StringUtils.equalsIgnoreCase(exceptionMessage, expectedException.getMessage()));

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).updateManagedUser(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        assertEquals(fakeUsername, paramsSent.get("username"));
    }

    @Test
    void deleteUser_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        String fakeUserId = UUID.randomUUID().toString();
        assertThrows(NullPointerException.class, ()-> userManagementService.deleteUser(null, fakeUserId, keycloakAdapter));
    }

    @Test
    void deleteUser_shouldThrowNullPointerExceptionWhenNullUserIdIsReceived() {
        assertThrows(NullPointerException.class, ()-> userManagementService.deleteUser(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void deleteUser_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        String fakeUserId = UUID.randomUUID().toString();
        assertThrows(NullPointerException.class, ()-> userManagementService.deleteUser(fakeAccessToken, fakeUserId, null));
    }

    @Test
    void deleteUser_shouldSucceedWhenNoExceptionsAreThrown() {
        String fakeUserId = UUID.randomUUID().toString();

        userManagementService.deleteUser(fakeUserId, fakeUserId, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).deleteManagedUser(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(fakeUserId, (String) paramsSent.get("userId")));
    }

    @Test
    void assignAdminRole_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> userManagementService.assignAdminRole(null, "some-user-id", keycloakAdapter));
    }

    @Test
    void assignAdminRole_shouldThrowNullPointerExceptionWhenNullUserIdIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> userManagementService.assignAdminRole(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void assignAdminRole_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> userManagementService.assignAdminRole(fakeAccessToken, "some-user-id", null));
    }

    @Test
    void assignAdminRole_shouldSucceedWhenNoExceptionsAreThrown() {
        var userId = "some-user-id";

        userManagementService.assignAdminRole(fakeAccessToken, userId, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).assignAdminRole(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;
        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(userId, (String) paramsSent.get("userId")));
    }

    @Test
    void removeAdminRole_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        assertThrows(NullPointerException.class, ()-> userManagementService.removeAdminRole(null, "someUserId", keycloakAdapter));
    }

    @Test
    void removeAdminRole_shouldThrowNullPointerExceptionWhenNullUserIdIsReceived() {
        assertThrows(NullPointerException.class, ()-> userManagementService.removeAdminRole(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void removeAdminRole_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        assertThrows(NullPointerException.class, ()-> userManagementService.removeAdminRole(fakeAccessToken, "aUserId", null));
    }

    @Test
    void removeAdminRole_shouldSucceedWhenNoExceptionsAreThrown() {
        var fakeUserId = UUID.randomUUID().toString();

        userManagementService.removeAdminRole(fakeAccessToken, fakeUserId, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).removeAdminRole(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(fakeUserId, (String) paramsSent.get("userId")));
    }

    private void assertMandatoryParamsForKeycloakSearch(Map<String, Object> paramsUsedToSearchUsers, int expectedParamsCount) {
        assertTrue(paramsUsedToSearchUsers.containsKey("firstResult"));
        assertTrue(paramsUsedToSearchUsers.containsKey("maxResults"));
        assertTrue(paramsUsedToSearchUsers.containsKey("accessToken"));
        assertEquals(expectedParamsCount, paramsUsedToSearchUsers.size());
    }

    private Supplier<IDPUserDTO> fakeUserSupplier() {
        return () -> {
            IDPGroupDTO fakeGroup = fakeGroupSupplier().get();
            Set<IDPGroupDTO> fakeGroups = Set.of(fakeGroup);
            Set<String> fakeRealmRoles = Set.of("custom-role");
            Map<String, Set<String>> fakeClientRoles = Map.of("some-client", Set.of("another-role"));

            return IDPUserDTO.builder()
                    .id(UUID.randomUUID().toString())
                    .username("some-random-username")
                    .firstName("Random")
                    .lastName("Random")
                    .email("someemail@somedomain.com")
                    .groups(fakeGroups)
                    .realmRoles(fakeRealmRoles)
                    .clientRoles(fakeClientRoles)
                    .build();
        };
    }

    private Supplier<IDPGroupDTO> fakeGroupSupplier() {
        return ()-> IDPGroupDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("my-group")
                .build();
    }
}
