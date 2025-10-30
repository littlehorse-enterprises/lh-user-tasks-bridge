package io.littlehorse.usertasks.models.responses;

import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code IDPGroupListDTO} is a Data Transfer Object that contains a Set of {@code io.littlehorse.usertasks.models.responses.IDPGroupDTO}
 *
 * @see IDPGroupDTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IDPGroupListDTO {
    private Set<IDPGroupDTO> groups;
}
