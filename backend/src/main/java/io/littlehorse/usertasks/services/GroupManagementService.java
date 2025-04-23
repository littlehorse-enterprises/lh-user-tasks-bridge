package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateGroupRequest;
import jakarta.validation.ValidationException;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import java.util.Map;
import java.util.Objects;

import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.ACCESS_TOKEN_MAP_KEY;
import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.USER_GROUP_NAME_MAP_KEY;

@Service
public class GroupManagementService {

    public void createGroupInIdentityProvider(@NonNull String accessToken, @NonNull CreateGroupRequest request,
                                              @NonNull IStandardIdentityProviderAdapter identityProviderAdapter) {
        Map<String, Object> params = Map.of(ACCESS_TOKEN_MAP_KEY, accessToken, USER_GROUP_NAME_MAP_KEY, request.getName());

        UserGroupDTO userGroup = identityProviderAdapter.getUserGroup(params);

        if (Objects.nonNull(userGroup)) {
            throw new ValidationException("Group already exists with the requested name!");
        }

        identityProviderAdapter.createGroup(params);
    }
}
