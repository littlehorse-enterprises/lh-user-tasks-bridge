package io.littlehorse.usertasks.idp_adapters;

import java.util.Map;
import java.util.Set;

/**
 * Interface that defines standard IdP methods to be implemented in all the IdP custom adapters
 */
public interface IStandardIdentityProviderAdapter {
    Set<String> getUserGroups(Map<String, Object> params);

    Set<String> getMyUserGroups(Map<String, Object> params);
    Set<String> getUsers(Map<String, Object> params);
}
