package io.littlehorse.usertasks.services;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.idp_adapters.keycloak.KeycloakAdapter;
import io.littlehorse.usertasks.models.requests.IDPUserSearchRequestFilter;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.Map;

@Service
@Slf4j
public class UserManagementService {

    @NonNull
    public IDPUserListDTO listUsersFromIdentityProvider(@NonNull String accessToken, @NonNull IStandardIdentityProviderAdapter identityProviderAdapter,
                                   @NonNull IDPUserSearchRequestFilter requestFilter, int firstResult, int maxResults) {
        Map<String, Object> params = null;

        if (identityProviderAdapter instanceof KeycloakAdapter) {
            params = buildParamsForUsersSearchInKeycloak(accessToken, requestFilter, firstResult, maxResults);
        }

        return identityProviderAdapter.getManagedUsers(params);
    }

    private Map<String, Object> buildParamsForUsersSearchInKeycloak(String accessToken, IDPUserSearchRequestFilter requestFilter,
                                                                    int firstResult, int maxResults) {
        Map<String, Object> params = new HashMap<>();

        if (StringUtils.isNotBlank(requestFilter.getEmail())) {
            params.put("email", requestFilter.getEmail());
        }

        if (StringUtils.isNotBlank(requestFilter.getFirstName())) {
            params.put("firstName", requestFilter.getFirstName());
        }

        if (StringUtils.isNotBlank(requestFilter.getLastName())) {
            params.put("lastName", requestFilter.getLastName());
        }

        if (StringUtils.isNotBlank(requestFilter.getUsername())) {
            params.put("username", requestFilter.getUsername());
        }

        if (StringUtils.isNotBlank(requestFilter.getUserGroupId())) {
            params.put("userGroupId", requestFilter.getUserGroupId());
        }

        params.put("accessToken", accessToken);
        params.put("firstResult", firstResult);
        params.put("maxResults", maxResults);

        return params;
    }
}
