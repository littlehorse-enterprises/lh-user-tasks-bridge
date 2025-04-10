package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.CreateManagedUserRequest;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.requests.PasswordUpsertRequest;
import io.littlehorse.usertasks.models.responses.IDPUserDTO;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Optional;

import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.ACCESS_TOKEN_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USER_ID_MAP_KEY;

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

    public void setPassword(@NonNull String accessToken, @NonNull String userId, @NonNull PasswordUpsertRequest requestBody,
                            @NonNull IStandardIdentityProviderAdapter identityProviderHandler) {
        Map<String, Object> params = Map.of(ACCESS_TOKEN_MAP_KEY, accessToken,
                "password", requestBody.getPassword().trim(),
                "isTemporary", requestBody.isTemporary());

        identityProviderHandler.setPassword(userId, params);
    }

    public Optional<IDPUserDTO> getUserFromIdentityProvider(@NonNull String accessToken, @NonNull String userId,
                                                           @NonNull IStandardIdentityProviderAdapter identityProviderHandler) {
        Map<String, Object> params = Map.of(ACCESS_TOKEN_MAP_KEY, accessToken, USER_ID_MAP_KEY, userId);

        return Optional.ofNullable(identityProviderHandler.getManagedUser(params));
    }
}
