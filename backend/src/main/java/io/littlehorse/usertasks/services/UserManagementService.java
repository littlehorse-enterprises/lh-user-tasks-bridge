package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.CreateManagedUserRequest;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;

@Service
@Slf4j
public class UserManagementService {

    @NonNull
    public IDPUserListDTO listUsersFromIdentityProvider(@NonNull String accessToken, @NonNull IStandardIdentityProviderAdapter identityProviderAdapter,
                                   @NonNull IDPUserSearchRequestFilter requestFilter, int firstResult, int maxResults) {
        Map<String, Object> params = null;

        if (identityProviderAdapter instanceof KeycloakAdapter) {
            params = KeycloakAdapter.buildParamsForUsersSearch(accessToken, requestFilter, firstResult, maxResults);
        }

        return identityProviderAdapter.getManagedUsers(params);
    }

    public void createUserInIdentityProvider(@NonNull String accessToken, @NonNull CreateManagedUserRequest requestBody,
                                             @NonNull IStandardIdentityProviderAdapter identityProviderAdapter) {
        Map<String, Object> params = null;

        if (identityProviderAdapter instanceof KeycloakAdapter) {
            params = KeycloakAdapter.buildParamsForUserCreation(accessToken, requestBody);
        }

        identityProviderAdapter.createUser(params);
    }
}
