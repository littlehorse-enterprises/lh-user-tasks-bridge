package io.littlehorse.usertasks.models.responses;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Set;

/**
 * {@code UserTaskRunListDTO} is a Data Transfer Object that contains a collection of {@code io.littlehorse.sdk.common.proto.UserTaskRun}
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskRunList
 * @see io.littlehorse.sdk.common.proto.UserTaskRun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskRunListDTO {
    private Set<SimpleUserTaskRunDTO> userTasks;
}
