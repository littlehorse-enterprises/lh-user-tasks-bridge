package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.responses.IDPGroupDTO;
import io.littlehorse.usertasks.models.responses.IDPUserDTO;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.util.CollectionUtils;

import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
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
