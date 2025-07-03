package io.littlehorse.usertasks.models.responses;

import jakarta.validation.constraints.NotBlank;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code UserTaskDefListDTO} is a Data Transfer Object that contains a Set of {@code io.littlehorse.sdk.common.proto.UserTaskDefId}
 * * and a bookmark used for pagination purposes
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskDefId
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskDefListDTO {
    @NotBlank
    private Set<String> userTaskDefNames;

    private String bookmark;
}
