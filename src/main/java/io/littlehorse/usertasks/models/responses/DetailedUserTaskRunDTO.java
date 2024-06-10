package io.littlehorse.usertasks.models.responses;

import io.littlehorse.sdk.common.proto.UserTaskDef;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.usertasks.models.common.UserTaskVariableValue;
import io.littlehorse.usertasks.util.UserTaskStatus;
import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

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
    private String userGroup;
    private String userId;
    @NonNull
    private UserTaskStatus status;
    private String notes;
    @NonNull
    private LocalDateTime scheduledTime;
    @NonNull
    private List<UserTaskFieldDTO> fields;
    private Map<String, UserTaskVariableValue> results;

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
                .results(fromServerTypeResults(userTaskRun.getResultsMap()))
                .build();
    }

    private static Map<String, UserTaskVariableValue> fromServerTypeResults(Map<String, VariableValue> serverResults) {
        return serverResults.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, entry -> UserTaskVariableValue.fromServerType(entry.getValue())));
    }
}
