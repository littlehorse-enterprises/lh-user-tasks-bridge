package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateGroupRequest;
import jakarta.validation.ValidationException;
import org.apache.commons.lang3.StringUtils;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.Mockito.*;

@SuppressWarnings("unchecked")
class GroupManagementServiceTest {
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
}
