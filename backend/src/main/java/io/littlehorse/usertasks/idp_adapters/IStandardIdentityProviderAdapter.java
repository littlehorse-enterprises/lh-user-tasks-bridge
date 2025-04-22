package io.littlehorse.usertasks.idp_adapters;

import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.models.responses.IDPUserDTO;
import io.littlehorse.usertasks.models.responses.IDPUserListDTO;
import io.littlehorse.usertasks.models.responses.UserGroupListDTO;
import io.littlehorse.usertasks.models.responses.UserListDTO;

import java.util.Map;

/**
 * Interface that defines standard IdP methods to be implemented in all the IdP custom adapters
 */
public interface IStandardIdentityProviderAdapter {
    UserGroupListDTO getUserGroups(Map<String, Object> params);

    UserGroupListDTO getMyUserGroups(Map<String, Object> params);

    UserListDTO getUsers(Map<String, Object> params);

    IDPUserListDTO getManagedUsers(Map<String, Object> params);

    UserDTO getUserInfo(Map<String, Object> params);

    UserGroupDTO getUserGroup(Map<String, Object> params);

    void validateUserGroup(String userGroupId, String accessToken);

    void validateAssignmentProperties(Map<String, Object> params);

    void createManagedUser(Map<String, Object> params);

    void setPassword(String userId, Map<String, Object> params);

    IDPUserDTO getManagedUser(Map<String, Object> params);

    void updateManagedUser(Map<String, Object> params);

    void deleteManagedUser(Map<String, Object> params);

    void assignAdminRole(Map<String, Object> params);

    void removeAdminRole(Map<String, Object> params);

    void joinGroup(Map<String, Object> params);
}
