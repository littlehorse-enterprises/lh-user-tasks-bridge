package io.littlehorse.usertasks.models.responses;

import io.littlehorse.usertasks.idp_adapters.IStandardIdentityProviderAdapter;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;
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

    public void addAssignmentDetails(@NonNull String accessToken, @NonNull IStandardIdentityProviderAdapter identityProviderHandler) {
        if (!CollectionUtils.isEmpty(this.getUserTasks())) {
            this.getUserTasks().forEach(userTaskRunDTO -> {
                Map<String, Object> params = new HashMap<>();
                params.put("accessToken", accessToken);

                addAssignedUserInfo(identityProviderHandler, userTaskRunDTO, params);
                addAssignedUserGroupInfo(identityProviderHandler, userTaskRunDTO, params);
            });
        }
    }

    private void addAssignedUserInfo(IStandardIdentityProviderAdapter identityProviderHandler,
                                     SimpleUserTaskRunDTO userTaskRunDTO, Map<String, Object> params) {
        if (Objects.nonNull(userTaskRunDTO.getUser())) {
            params.put("userId", userTaskRunDTO.getUser().getId());
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

    private void addAssignedUserGroupInfo(IStandardIdentityProviderAdapter identityProviderHandler, SimpleUserTaskRunDTO userTaskRunDTO, Map<String, Object> params) {
        if (Objects.nonNull(userTaskRunDTO.getUserGroup())) {
            params.put("userGroupId", userTaskRunDTO.getUserGroup().getId());
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
}
