package io.littlehorse.usertasks.models.responses;

import lombok.Builder;
import lombok.Data;
import org.keycloak.representations.idm.GroupRepresentation;
import org.keycloak.representations.idm.UserRepresentation;
import org.springframework.util.CollectionUtils;

import java.util.*;
import java.util.stream.Collectors;

/**
 * {@code IDPUserDTO} is a Data Transfer Object that contains information about a specific User from an Identity Provider.
 * This is a less complex and standardized model in charge of providing user-related data for management purposes.
 */
@Builder
@Data
public class IDPUserDTO {
    private String id;
    private String username;
    private String firstName;
    private String lastName;
    private String email;
    private Set<IDPGroupDTO> groups;
    private Set<String> realmRoles;
    private Map<String, Set<String>> clientRoles;

    /**
     * Transforms a {@code UserRepresentation}, and optionally its respective collection of {@code GroupRepresentation},
     * into an {@code IDPUserDTO}.
     * @return An object of type {@link  io.littlehorse.usertasks.models.responses.IDPUserDTO}
     * @see org.keycloak.representations.idm.UserRepresentation
     * @see org.keycloak.representations.idm.GroupRepresentation
     */
    public static IDPUserDTO transform(UserRepresentation userRepresentation,
                                       Collection<GroupRepresentation> groupsRepresentation) {
        Set<IDPGroupDTO> foundGroups = getGroupsFromKeycloakGroupsRepresentation(groupsRepresentation);

        return IDPUserDTO.builder()
                .id(userRepresentation.getId())
                .username(userRepresentation.getUsername())
                .firstName(userRepresentation.getFirstName())
                .lastName(userRepresentation.getLastName())
                .email(userRepresentation.getEmail())
                .groups(foundGroups)
                .build();
    }

    private static Set<IDPGroupDTO> getGroupsFromKeycloakGroupsRepresentation(Collection<GroupRepresentation> groupsRepresentation) {
        if (!CollectionUtils.isEmpty(groupsRepresentation)) {
            return groupsRepresentation.stream()
                    .filter(Objects::nonNull)
                    .map(groupRepresentation -> IDPGroupDTO.transform(groupRepresentation, null))
                    .collect(Collectors.toUnmodifiableSet());
        }

        return Collections.emptySet();
    }
}
