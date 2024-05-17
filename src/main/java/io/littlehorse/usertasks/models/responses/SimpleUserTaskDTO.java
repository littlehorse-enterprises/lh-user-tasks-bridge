package io.littlehorse.usertasks.models.responses;

import io.littlehorse.usertasks.util.UserTaskStatus;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * {@code SimpleUserTaskDTO} is a Data Transfer Object that contains some basic information about a
 * specific {@code io.littlehorse.sdk.common.proto.UserTaskRun}
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskRun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleUserTaskDTO {
    @NotBlank
    private String id;
    @NotBlank
    private String userTaskDefId;
    @Nullable
    private String userGroup;
    @Nullable
    private String userId;
    @NotNull
    private UserTaskStatus status;
    @Nullable
    private String notes;
    @NotNull
    private LocalDateTime scheduledTime;
    //TODO: Pending to add "events" and "results" fields
}
