package io.littlehorse.usertasks.models.responses;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

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
