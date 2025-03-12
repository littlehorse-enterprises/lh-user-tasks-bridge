package io.littlehorse.usertasks.models.responses;

import io.littlehorse.usertasks.configurations.CustomIdentityProviderProperties;
import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.util.enums.CustomUserIdClaim;
import lombok.*;
import org.springframework.util.CollectionUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * {@code UserTaskRunListDTO} is a Data Transfer Object that contains a Set of {@code io.littlehorse.sdk.common.proto.UserTaskRun}
 * and a bookmark used for pagination purposes
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskRunList
 * @see io.littlehorse.sdk.common.proto.UserTaskRun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskRunListDTO {
    private Set<SimpleUserTaskRunDTO> userTasks;
    private String bookmark;

    public void addAssignmentDetails(@NonNull String accessToken, @NonNull IStandardIdentityProviderAdapter identityProviderHandler,
                                     CustomIdentityProviderProperties customIdentityProviderProperties) {
        if (!CollectionUtils.isEmpty(this.getUserTasks())) {
            for (SimpleUserTaskRunDTO userTaskRunDTO : this.getUserTasks()) {
                addAssignedUserInfo(accessToken, identityProviderHandler, userTaskRunDTO, customIdentityProviderProperties);
                addAssignedUserGroupInfo(accessToken, identityProviderHandler, userTaskRunDTO);
            }
        }
    }

    private void addAssignedUserInfo(String accessToken, IStandardIdentityProviderAdapter identityProviderHandler,
                                     SimpleUserTaskRunDTO userTaskRunDTO,
                                     CustomIdentityProviderProperties customIdentityProviderProperties) {
        if (Objects.nonNull(userTaskRunDTO.getUser())) {
            Map<String, Object> params = getIdentityProviderSearchUserParams(accessToken, userTaskRunDTO, customIdentityProviderProperties);

            UserDTO userDTO = identityProviderHandler.getUserInfo(params);

            if (Objects.nonNull(userDTO)) {
                userTaskRunDTO.setUser(userDTO);
            } else {
                userTaskRunDTO.setUser(UserDTO.builder()
                        .id(userTaskRunDTO.getUser().getId())
                        .valid(false)
                        .build());
            }
        }
    }

    private void addAssignedUserGroupInfo(String accessToken, IStandardIdentityProviderAdapter identityProviderHandler,
                                          SimpleUserTaskRunDTO userTaskRunDTO) {
        if (Objects.nonNull(userTaskRunDTO.getUserGroup())) {
            Map<String, Object> params = Map.of("accessToken", accessToken, "userGroupName", userTaskRunDTO.getUserGroup().getId());

            UserGroupDTO userGroupDTO = identityProviderHandler.getUserGroup(params);

            if (Objects.nonNull(userGroupDTO)) {
                userTaskRunDTO.setUserGroup(userGroupDTO);
            } else {
                userTaskRunDTO.setUserGroup(UserGroupDTO.builder()
                        .id(userTaskRunDTO.getUserGroup().getId())
                        .valid(false)
                        .build());
            }
        }
    }

    private Map<String, Object> getIdentityProviderSearchUserParams(String accessToken, SimpleUserTaskRunDTO userTaskRunDTO, CustomIdentityProviderProperties customIdentityProviderProperties) {
        Map<String, Object> standardParams = Map.of("accessToken", accessToken, "userId", userTaskRunDTO.getUser().getId());

        Map<String, Object> params = new HashMap<>(standardParams);

        CustomUserIdClaim configuredUserIdClaim = customIdentityProviderProperties.getUserIdClaim();

        if (configuredUserIdClaim.equals(CustomUserIdClaim.EMAIL)) {
            params.put("email", userTaskRunDTO.getUser().getId());
        } else if (configuredUserIdClaim.equals(CustomUserIdClaim.PREFERRED_USERNAME)) {
            params.put("username", userTaskRunDTO.getUser().getId());
        }
        return params;
    }
}
