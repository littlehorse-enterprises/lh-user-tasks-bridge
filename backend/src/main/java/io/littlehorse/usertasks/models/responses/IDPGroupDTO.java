package io.littlehorse.usertasks.models.responses;

import jakarta.validation.constraints.NotBlank;
import lombok.Builder;
import lombok.Data;
import org.keycloak.representations.idm.GroupRepresentation;

/**
 * {@code IDPGroupDTO} is a Data Transfer Object that contains information about a specific Group from an Identity Provider.
 * This is a less complex and standardized model in charge of providing group-related data for management purposes.
 */
@Builder
@Data
public class IDPGroupDTO {
    @NotBlank
    private String id;

    @NotBlank
    private String name;

    /**
     * Transforms a {@code GroupRepresentation}, and optionally its respective collection of {@code UserRepresentation} (members),
     * into an {@code IDPGroupDTO}.
     * @return An object of type {@link  io.littlehorse.usertasks.models.responses.IDPGroupDTO}
     * @see org.keycloak.representations.idm.GroupRepresentation
     * @see org.keycloak.representations.idm.UserRepresentation
     */
    public static IDPGroupDTO transform(GroupRepresentation groupRepresentation) {
        return IDPGroupDTO.builder()
                .id(groupRepresentation.getId())
                .name(groupRepresentation.getName())
                .build();
    }
}
