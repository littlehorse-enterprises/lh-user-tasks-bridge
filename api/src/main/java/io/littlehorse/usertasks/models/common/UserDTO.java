package io.littlehorse.usertasks.models.common;

import io.littlehorse.sdk.common.proto.UserTaskRun;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;
import org.keycloak.representations.idm.UserRepresentation;

import java.util.function.Function;

/**
 * {@code UserDTO} is a Data Transfer Object that contains information about a specific User from an Identity Provider
 */
@Builder
@AllArgsConstructor
@Data
public class UserDTO {
    @NotBlank
    private String id;
    private String email;
    private String username;
    private String firstName;
    private String lastName;

    /**
     * Transforms a {@code UserRepresentation} into a {@code UserDTO}
     *
     * @return An object of type {@code io.littlehorse.usertasks.models.common.UserDTO}
     * @see org.keycloak.representations.idm.UserRepresentation
     */
    public static Function<UserRepresentation, UserDTO> transform() {
        return userRepresentation -> UserDTO.builder()
                .id(userRepresentation.getId())
                .email(userRepresentation.getEmail())
                .username(userRepresentation.getUsername())
                .firstName(userRepresentation.getFirstName())
                .lastName(userRepresentation.getLastName())
                .build();
    }

    /**
     * Builds a partial {@code UserDTO} based on a {@code UserTaskRun} that might have a userId assigned
     *
     * @param userTaskRun UserTaskRun from which the userGroup might be taken from.
     * @return A partial representation of a {@code UserDTO} that only has the ID property set, or null in case that
     * the UserTaskRun does not have a userId set.
     * @see UserTaskRun
     */
    public static UserDTO partiallyBuildFromUserTaskRun(@NonNull UserTaskRun userTaskRun) {
        return userTaskRun.hasUserId()
                ? UserDTO.builder().id(userTaskRun.getUserId()).build()
                : null;
    }
}
