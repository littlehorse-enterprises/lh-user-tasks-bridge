package io.littlehorse.usertasks.models.responses;

import io.littlehorse.sdk.common.proto.UserTaskDef;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.usertasks.util.UserTaskStatus;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.util.List;

import static io.littlehorse.usertasks.util.DateUtil.timestampToLocalDateTime;

/**
 * {@code DetailedUserTaskRunDTO} is a Data Transfer Object that contains detailed information about a
 * specific {@code io.littlehorse.sdk.common.proto.UserTaskRun} and its respective {@code io.littlehorse.sdk.common.proto.UserTaskDef}
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskRun
 * @see io.littlehorse.sdk.common.proto.UserTaskDef
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DetailedUserTaskRunDTO {
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
    @NotNull
    private List<UserTaskFieldDTO> fields;
    //TODO: Pending to add "results" field

    public static DetailedUserTaskRunDTO fromUserTaskRun(@NonNull UserTaskRun userTaskRun, @NonNull UserTaskDef userTaskDef) {
        var fields = userTaskDef.getFieldsList().stream()
                .map(UserTaskFieldDTO::fromServerUserTaskField)
                .toList();

        return DetailedUserTaskRunDTO.builder()
                .id(userTaskRun.getId().getUserTaskGuid())
                .wfRunId(userTaskRun.getId().getWfRunId().getId())
                .userTaskDefName(userTaskRun.getUserTaskDefId().getName())
                .userId(userTaskRun.getUserId())
                .userGroup(userTaskRun.getUserGroup())
                .notes(userTaskRun.getNotes())
                .status(UserTaskStatus.fromServerStatus(userTaskRun.getStatus()))
                .scheduledTime(timestampToLocalDateTime(userTaskRun.getScheduledTime()))
                .fields(fields)
                .build();
    }
}
