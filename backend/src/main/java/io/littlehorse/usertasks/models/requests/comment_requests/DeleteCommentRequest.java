package io.littlehorse.usertasks.models.requests.comment_requests;

import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.DeleteUserTaskRunCommentRequest;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class DeleteCommentRequest {

    private String wfRunId;

    private String userTaskRunGuid;

    private int commentId;

    public DeleteUserTaskRunCommentRequest toServerRequest(String userId) {
        DeleteUserTaskRunCommentRequest req = DeleteUserTaskRunCommentRequest.newBuilder()
                .setUserId(userId)
                .setUserCommentId(getCommentId())
                .setUserTaskRunId(UserTaskRunId.newBuilder()
                        .setWfRunId(LHLibUtil.wfRunIdFromString(getWfRunId()))
                        .setUserTaskGuid(getUserTaskRunGuid())
                        .build())
                .build();

        return req;
    }
}
