package io.littlehorse.usertasks.models.common;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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
}
