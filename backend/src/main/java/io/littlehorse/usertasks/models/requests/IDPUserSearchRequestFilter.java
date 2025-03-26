package io.littlehorse.usertasks.models.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Class that is used to easily map filters when searching Users in an Identity Provider
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IDPUserSearchRequestFilter {
    private String email;
    private String firstName;
    private String lastName;
    private String username;
    private String userGroupId;
}
