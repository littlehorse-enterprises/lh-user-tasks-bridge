package io.littlehorse.usertasks.models.requests;

import io.littlehorse.sdk.common.proto.CompleteUserTaskRunRequest;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.sdk.common.proto.WfRunId;
import io.littlehorse.usertasks.models.common.UserTaskVariableValue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import java.util.Map;
import java.util.stream.Collectors;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CompleteUserTaskRequest {
    @NotBlank
    private String wfRunId;

    @NotBlank
    private String userTaskRunGuid;

    @NotEmpty
    private Map<String, UserTaskVariableValue> results;

    public CompleteUserTaskRunRequest toServerRequest(@NonNull String userId) {
        Map<String, VariableValue> parsedResults = parseResultsToServerType();

        return CompleteUserTaskRunRequest.newBuilder()
                .setUserId(userId)
                .setUserTaskRunId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId(getWfRunId()).build())
                        .setUserTaskGuid(getUserTaskRunGuid())
                        .build())
                .putAllResults(parsedResults)
                .build();
    }

    private Map<String, VariableValue> parseResultsToServerType() {
        return this.results.entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey, entry -> entry.getValue()
                .toServerType()));
    }
}
