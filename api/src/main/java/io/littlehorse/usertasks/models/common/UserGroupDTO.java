package io.littlehorse.usertasks.models.common;

import io.littlehorse.sdk.common.proto.UserTaskRun;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.keycloak.representations.idm.GroupRepresentation;

import java.util.function.Function;

/**
 * {@code UserGroupDTO} is a Data Transfer Object that contains information about a specific UserGroup from an Identity Provider
 */
@Builder
@AllArgsConstructor
@Data
public class UserGroupDTO {
    @NotBlank
    private String id;
    @NotBlank
    private String name;

    /**
     * Transforms a {@code GroupRepresentation} into a {@code UserGroupDTO}
     *
     * @return An object of type {@code io.littlehorse.usertasks.models.common.UserGroupDTO}
     * @see org.keycloak.representations.idm.GroupRepresentation
     */
    public static Function<GroupRepresentation, UserGroupDTO> transform() {
        return groupRepresentation -> UserGroupDTO.builder()
                .id(groupRepresentation.getId())
                .name(groupRepresentation.getName())
                .build();
    }

    /**
     * Builds a partial {@code UserGroupDTO} based on a {@code UserTaskRun} that might have a userGroup assigned
     *
     * @param userTaskRun UserTaskRun from which the userGroup might be taken from.
     * @return A partial representation of a {@code UserGroupDTO} that only has the ID property set, or null in case that
     * the UserTaskRun does not have a userGroup set.
     * @see UserTaskRun
     */
    public static UserGroupDTO partiallyBuildFromUserTaskRun(@NonNull UserTaskRun userTaskRun) {
        return userTaskRun.hasUserGroup()
                ? UserGroupDTO.builder().id(userTaskRun.getUserGroup()).build()
                : null;

    }
}
