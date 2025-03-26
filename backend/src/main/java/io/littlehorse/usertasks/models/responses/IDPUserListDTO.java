package io.littlehorse.usertasks.models.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * {@code IDPUserListDTO} is a Data Transfer Object that contains a {@code Set} of {@code IDPUserDTO}
 *
 * @see java.util.Set
 * @see io.littlehorse.usertasks.models.responses.IDPUserDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IDPUserListDTO {
    private Set<IDPUserDTO> users;
}
