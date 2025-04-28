package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.requests.CreateGroupRequest;
import io.littlehorse.usertasks.models.responses.IDPGroupDTO;
import jakarta.validation.ValidationException;
import lombok.NonNull;
import org.springframework.stereotype.Service;

import javax.annotation.Nullable;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import static io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter.*;

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

    public Set<IDPGroupDTO> getGroups(@NonNull String accessToken, @Nullable String name, int firstResult, int maxResults,
                                      @NonNull IStandardIdentityProviderAdapter identityProviderHandler) {
        Map<String, Object> params = new HashMap<>();

        params.put(ACCESS_TOKEN_MAP_KEY, accessToken);
        params.put(USER_GROUP_NAME_MAP_KEY, name);
        params.put(FIRST_RESULT_MAP_KEY, firstResult);
        params.put(MAX_RESULTS_MAP_KEY, maxResults);

        params.entrySet().removeIf(entry -> Objects.isNull(entry.getValue()));

        return identityProviderHandler.getGroups(params);
    }
}
