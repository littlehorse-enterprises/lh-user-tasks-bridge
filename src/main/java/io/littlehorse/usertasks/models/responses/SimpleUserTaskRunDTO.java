package io.littlehorse.usertasks.models.responses;

import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.usertasks.util.UserTaskStatus;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

import static io.littlehorse.usertasks.util.DateUtil.timestampToLocalDateTime;

/**
 * {@code SimpleUserTaskRunDTO} is a Data Transfer Object that contains some basic information about a
 * specific {@code io.littlehorse.sdk.common.proto.UserTaskRun}
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskRun
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SimpleUserTaskRunDTO {
    @NotBlank
    private String id;
    @NotBlank
    private String wfRunId;
    @NotBlank
    private String userTaskDefName;
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

    public static SimpleUserTaskRunDTO fromUserTaskRun(UserTaskRun userTaskRun) {
        return SimpleUserTaskRunDTO.builder()
                .id(userTaskRun.getId().getUserTaskGuid())
                .wfRunId(userTaskRun.getId().getWfRunId().getId())
                .userTaskDefName(userTaskRun.getUserTaskDefId().getName())
                .userId(userTaskRun.getUserId())
                .userGroup(userTaskRun.getUserGroup())
                .notes(userTaskRun.getNotes())
                .status(UserTaskStatus.fromServerStatus(userTaskRun.getStatus()))
                .scheduledTime(timestampToLocalDateTime(userTaskRun.getScheduledTime()))
                .build();
    }
}
