package io.littlehorse.usertasks.models.common;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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
                .build();
    }
}
