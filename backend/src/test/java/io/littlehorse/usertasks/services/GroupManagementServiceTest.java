package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateGroupRequest;
import io.littlehorse.usertasks.models.requests.UpdateGroupRequest;
import io.littlehorse.usertasks.models.responses.IDPGroupDTO;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.CollectionUtils;

import java.util.Collections;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GroupManagementServiceTest {
    public static final int FIRST_RESULT_DEFAULT = 0;
    public static final int MAX_RESULTS_DEFAULT = 10;
    private final GroupManagementService groupManagementService = new GroupManagementService();

    private final IStandardIdentityProviderAdapter keycloakAdapter = mock(KeycloakAdapter.class);

    String fakeAccessToken = "some-fake-access-token";

    @Test
    void createGroupInIdentityProvider_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        CreateGroupRequest request = new CreateGroupRequest("some-group");

        assertThrows(NullPointerException.class,
                ()-> groupManagementService.createGroupInIdentityProvider(null, request, keycloakAdapter));
    }

    @Test
    void createGroupInIdentityProvider_shouldThrowNullPointerExceptionWhenNullRequestIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.createGroupInIdentityProvider(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void createGroupInIdentityProvider_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        CreateGroupRequest request = new CreateGroupRequest("some-group");

        assertThrows(NullPointerException.class,
                ()-> groupManagementService.createGroupInIdentityProvider(fakeAccessToken, request, null));
    }

    @Test
    void createGroupInIdentityProvider_shouldThrowValidationExceptionWhenNameIsAlreadyUsedByExistingGroup() {
        var groupName = "some-group";
        CreateGroupRequest request = new CreateGroupRequest(groupName);

        UserGroupDTO foundGroup = UserGroupDTO.builder()
                .id("some-id")
                .name(groupName)
                .build();

        when(keycloakAdapter.getUserGroup(anyMap())).thenReturn(foundGroup);

        ValidationException thrownException = assertThrows(ValidationException.class,
                () -> groupManagementService.createGroupInIdentityProvider(fakeAccessToken, request, keycloakAdapter));

        var expectedErrorMessage = "Group already exists with the requested name!";
        assertTrue(StringUtils.equalsIgnoreCase(expectedErrorMessage, thrownException.getMessage()));

        verify(keycloakAdapter).getUserGroup(anyMap());
        verify(keycloakAdapter, never()).createGroup(anyMap());
    }

    @Test
    void createGroupInIdentityProvider_shouldSucceedWhenNoExceptionsAreThrown() {
        var groupName = "some-group";
        CreateGroupRequest request = new CreateGroupRequest(groupName);

        when(keycloakAdapter.getUserGroup(anyMap())).thenReturn(null);

        groupManagementService.createGroupInIdentityProvider(fakeAccessToken, request, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getUserGroup(anyMap());
        verify(keycloakAdapter).createGroup(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;
        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(groupName, (String) paramsSent.get("userGroupName")));
    }

    @Test
    void getGroups_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.getGroups(null, null, FIRST_RESULT_DEFAULT, MAX_RESULTS_DEFAULT, keycloakAdapter));
    }

    @Test
    void getGroups_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.getGroups(fakeAccessToken, null, FIRST_RESULT_DEFAULT, MAX_RESULTS_DEFAULT, null));
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndNoNameIsReceivedAndNoGroupsAreFound() {
        when(keycloakAdapter.getGroups(anyMap())).thenReturn(Collections.emptySet());

        Set<IDPGroupDTO> foundGroups = groupManagementService.getGroups(fakeAccessToken, null, FIRST_RESULT_DEFAULT, MAX_RESULTS_DEFAULT, keycloakAdapter);

        assertTrue(CollectionUtils.isEmpty(foundGroups));

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getGroups(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 3;
        int expectedFirstResult = 0;
        int expectedMaxResults = 10;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertEquals(expectedFirstResult, paramsSent.get("firstResult"));
        assertEquals(expectedMaxResults, paramsSent.get("maxResults"));
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndNoNameIsReceivedAndGroupsAreFound() {
        IDPGroupDTO group1 = IDPGroupDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("my-group-1")
                .build();

        IDPGroupDTO group2 = IDPGroupDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("my-group-2")
                .build();

        Set<IDPGroupDTO> mappedGroups = Set.of(group1, group2);

        when(keycloakAdapter.getGroups(anyMap())).thenReturn(mappedGroups);

        Set<IDPGroupDTO> foundGroups = groupManagementService.getGroups(fakeAccessToken, null, FIRST_RESULT_DEFAULT, MAX_RESULTS_DEFAULT, keycloakAdapter);

        int expectedGroupsCount = 2;

        assertEquals(expectedGroupsCount, foundGroups.size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getGroups(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 3;
        int expectedFirstResult = 0;
        int expectedMaxResults = 10;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertEquals(expectedFirstResult, paramsSent.get("firstResult"));
        assertEquals(expectedMaxResults, paramsSent.get("maxResults"));
    }

    @Test
    void getGroups_shouldSucceedWhenNoExceptionsAreThrownAndNameIsReceivedAndGroupsAreFound() {
        IDPGroupDTO group1 = IDPGroupDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("my-group-1")
                .build();

        IDPGroupDTO group2 = IDPGroupDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("my-group-2")
                .build();

        IDPGroupDTO group3 = IDPGroupDTO.builder()
                .id(UUID.randomUUID().toString())
                .name("my-group-3")
                .build();

        var searchingName = "my-grou";

        Set<IDPGroupDTO> mappedGroups = Set.of(group1, group2, group3);

        when(keycloakAdapter.getGroups(anyMap())).thenReturn(mappedGroups);

        Set<IDPGroupDTO> foundGroups = groupManagementService.getGroups(fakeAccessToken, searchingName, FIRST_RESULT_DEFAULT, MAX_RESULTS_DEFAULT, keycloakAdapter);

        int expectedGroupsCount = 3;

        assertEquals(expectedGroupsCount, foundGroups.size());

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getGroups(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 4;
        int expectedFirstResult = 0;
        int expectedMaxResults = 10;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertEquals(expectedFirstResult, paramsSent.get("firstResult"));
        assertEquals(expectedMaxResults, paramsSent.get("maxResults"));
    }

    @Test
    void updateGroup_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        UpdateGroupRequest request = new UpdateGroupRequest("some-group");

        assertThrows(NullPointerException.class,
                ()-> groupManagementService.updateGroup(null, UUID.randomUUID().toString(), request, keycloakAdapter));
    }

    @Test
    void updateGroup_shouldThrowNullPointerExceptionWhenNullGroupIdIsReceived() {
        UpdateGroupRequest request = new UpdateGroupRequest("some-group");

        assertThrows(NullPointerException.class,
                ()-> groupManagementService.updateGroup(fakeAccessToken, null, request, keycloakAdapter));
    }

    @Test
    void updateGroup_shouldThrowNullPointerExceptionWhenNullRequestIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.updateGroup(fakeAccessToken, UUID.randomUUID().toString(), null, keycloakAdapter));
    }

    @Test
    void updateGroup_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        UpdateGroupRequest request = new UpdateGroupRequest("some-group");

        assertThrows(NullPointerException.class,
                ()-> groupManagementService.updateGroup(fakeAccessToken, UUID.randomUUID().toString(), request, null));
    }

    @Test
    void updateGroup_shouldThrowValidationExceptionWhenThereIsAnExistingGroupAlreadyUsingTheRequestedName() {
        String groupIdBeingUpdated = UUID.randomUUID().toString();
        String groupName = "some-group";
        UpdateGroupRequest request = new UpdateGroupRequest(groupName);
        String groupIdFromAnotherGroup = UUID.randomUUID().toString();

        UserGroupDTO foundGroup = new UserGroupDTO(groupIdFromAnotherGroup, groupName, true);

        when(keycloakAdapter.getUserGroup(anyMap())).thenReturn(foundGroup);

        ValidationException thrownException = assertThrows(ValidationException.class,
                () -> groupManagementService.updateGroup(fakeAccessToken, groupIdBeingUpdated, request, keycloakAdapter));

        assertTrue(StringUtils.equalsIgnoreCase("Group already exists with the requested name!", thrownException.getMessage()));

        verify(keycloakAdapter).getUserGroup(anyMap());
        verify(keycloakAdapter, never()).updateGroup(anyMap());
    }

    @Test
    void updateGroup_shouldSucceedWhenNoExceptionsAreThrown() {
        String groupIdBeingUpdated = UUID.randomUUID().toString();
        String groupName = "some-group";
        UpdateGroupRequest request = new UpdateGroupRequest(groupName);

        when(keycloakAdapter.getUserGroup(anyMap())).thenReturn(null);

        groupManagementService.updateGroup(fakeAccessToken, groupIdBeingUpdated, request, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).getUserGroup(anyMap());
        verify(keycloakAdapter).updateGroup(argumentCaptor.capture());

        Map<String, Object> paramsSentToUpdate = argumentCaptor.getValue();

        int expectedParamsCount = 3;

        assertEquals(expectedParamsCount, paramsSentToUpdate.size());
        assertTrue(StringUtils.equalsIgnoreCase(groupIdBeingUpdated, (String) paramsSentToUpdate.get("userGroupId")));
        assertTrue(StringUtils.equalsIgnoreCase(groupName, (String) paramsSentToUpdate.get("userGroupName")));
    }

    @Test
    void deleteGroup_shouldThrowNullPointerExceptionWhenNullAccessTokenIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.deleteGroup(null, UUID.randomUUID().toString(), keycloakAdapter));
    }

    @Test
    void deleteGroup_shouldThrowNullPointerExceptionWhenNullGroupIdIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.deleteGroup(fakeAccessToken, null, keycloakAdapter));
    }

    @Test
    void deleteGroup_shouldThrowNullPointerExceptionWhenNullIdentityProviderAdapterIsReceived() {
        assertThrows(NullPointerException.class,
                ()-> groupManagementService.deleteGroup(fakeAccessToken, UUID.randomUUID().toString(), null));
    }

    @Test
    void deleteGroup_shouldSucceedWhenNoExceptionsAreThrown() {
        String fakeGroupId = UUID.randomUUID().toString();
        groupManagementService.deleteGroup(fakeAccessToken, fakeGroupId, keycloakAdapter);

        ArgumentCaptor<Map<String, Object>> argumentCaptor = ArgumentCaptor.forClass(Map.class);

        verify(keycloakAdapter).deleteGroup(argumentCaptor.capture());

        Map<String, Object> paramsSent = argumentCaptor.getValue();

        int expectedParamsCount = 2;

        assertEquals(expectedParamsCount, paramsSent.size());
        assertTrue(StringUtils.equalsIgnoreCase(fakeGroupId, (String) paramsSent.get("userGroupId")));
    }
}
