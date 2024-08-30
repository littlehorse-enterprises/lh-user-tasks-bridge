package io.littlehorse.usertasks.models.responses;

import io.littlehorse.usertasks.models.common.UserGroupDTO;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * {@code UserGroupListDTO} is a Data Transfer Object that contains a Set of {@code io.littlehorse.usertasks.models.common.UserGroupDTO}
 *
 * @see UserGroupDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserGroupListDTO {
    private Set<UserGroupDTO> groups;
}
