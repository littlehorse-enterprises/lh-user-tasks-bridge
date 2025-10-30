package io.littlehorse.usertasks.models.responses;

import io.littlehorse.usertasks.models.common.UserDTO;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code UserListDTO} is a Data Transfer Object that contains a {@code Set} of {@code UserDTO}
 *
 * @see java.util.Set
 * @see io.littlehorse.usertasks.models.common.UserDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListDTO {
    private Set<UserDTO> users;
}
