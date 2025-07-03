package io.littlehorse.usertasks.models.requests.comment_requests;

import io.littlehorse.sdk.common.LHLibUtil;
import io.littlehorse.sdk.common.proto.EditUserTaskRunCommentRequest;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EditCommentRequest {

    private String wfRunId;

    private String userTaskRunGuid;

    private String comment;

    private int commentId;

    public EditUserTaskRunCommentRequest toServerRequest(String userId) {
        EditUserTaskRunCommentRequest req = EditUserTaskRunCommentRequest.newBuilder()
                .setComment(getComment())
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
