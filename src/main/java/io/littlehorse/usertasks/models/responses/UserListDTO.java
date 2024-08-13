package io.littlehorse.usertasks.models.responses;

import io.littlehorse.usertasks.models.common.UserDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * {@code UserListDTO} is a Data Transfer Object that contains a Set of {@code io.littlehorse.usertasks.models.common.UserDTO}
 *
 * @see io.littlehorse.usertasks.models.common.UserDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserListDTO {
    private Set<UserDTO> users;
}
