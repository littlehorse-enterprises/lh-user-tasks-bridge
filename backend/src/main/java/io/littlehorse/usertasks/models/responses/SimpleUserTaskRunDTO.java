package io.littlehorse.usertasks.models.responses;

import static io.littlehorse.usertasks.util.DateUtil.timestampToLocalDateTime;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.usertasks.models.common.UserDTO;
import io.littlehorse.usertasks.models.common.UserGroupDTO;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

/**
 * {@code SimpleUserTaskRunDTO} is a Data Transfer Object that contains some basic information about a
 * specific {@code io.littlehorse.sdk.common.proto.UserTaskRun}
 *
 * @see UserTaskRun
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

    private UserGroupDTO userGroup;
    private UserDTO user;

    @NotNull
    private UserTaskStatus status;

    private String notes;

    @NotNull
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime scheduledTime;

    public static SimpleUserTaskRunDTO fromUserTaskRun(@NonNull UserTaskRun userTaskRun) {
        return SimpleUserTaskRunDTO.builder()
                .id(userTaskRun.getId().getUserTaskGuid())
                .wfRunId(userTaskRun.getId().getWfRunId().getId())
                .userTaskDefName(userTaskRun.getUserTaskDefId().getName())
                .user(UserDTO.partiallyBuildFromUserTaskRun(userTaskRun))
                .userGroup(UserGroupDTO.partiallyBuildFromUserTaskRun(userTaskRun))
                .notes(userTaskRun.getNotes())
                .status(UserTaskStatus.fromServerStatus(userTaskRun.getStatus()))
                .scheduledTime(timestampToLocalDateTime(userTaskRun.getScheduledTime()))
                .build();
    }
}
