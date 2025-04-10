package io.littlehorse.usertasks.models.responses;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.util.CollectionUtils;

import java.util.Collection;
import java.util.Collections;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * {@code IDPGroupDTO} is a Data Transfer Object that contains information about a specific Group from an Identity Provider.
 * This is a less complex and standardized model in charge of providing group-related data for management purposes.
 */
@Builder
@Data
public class IDPGroupDTO {
    private String id;
    private String name;
    @JsonInclude(JsonInclude.Include.NON_EMPTY)
    private Set<IDPUserDTO> members;

    /**
     * Transforms a {@code GroupRepresentation}, and optionally its respective collection of {@code UserRepresentation} (members),
     * into an {@code IDPGroupDTO}.
     * @return An object of type {@link  io.littlehorse.usertasks.models.responses.IDPGroupDTO}
     * @see org.keycloak.representations.idm.GroupRepresentation
     * @see org.keycloak.representations.idm.UserRepresentation
     */
    public static IDPGroupDTO transform(GroupRepresentation groupRepresentation, Collection<UserRepresentation> usersRepresentation) {
        Set<IDPUserDTO> foundMembers = getMembersFromKeycloakUserRepresentation(usersRepresentation);

        return IDPGroupDTO.builder()
                .id(groupRepresentation.getId())
                .name(groupRepresentation.getName())
                .members(foundMembers)
                .build();
    }

    private static Set<IDPUserDTO> getMembersFromKeycloakUserRepresentation(Collection<UserRepresentation> usersRepresentation) {
        if (!CollectionUtils.isEmpty(usersRepresentation)) {
            return usersRepresentation.stream()
                    .filter(Objects::nonNull)
                    .map(userRepresentation -> IDPUserDTO.transform(userRepresentation, null))
                    .collect(Collectors.toUnmodifiableSet());
        }

        return Collections.emptySet();
    }
}
