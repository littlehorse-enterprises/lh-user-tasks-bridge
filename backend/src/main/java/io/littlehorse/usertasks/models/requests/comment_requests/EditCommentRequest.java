package io.littlehorse.usertasks.models.requests.comment_requests;

import io.littlehorse.sdk.common.proto.EditUserTaskRunCommentRequest;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.WfRunId;
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


    public EditUserTaskRunCommentRequest toServer(String userId){
        EditUserTaskRunCommentRequest req = EditUserTaskRunCommentRequest.newBuilder()
        .setComment(getComment())
        .setUserId(userId)
        .setUserCommentId(getCommentId())
        .setUserTaskRunId(
        UserTaskRunId.newBuilder()
            .setWfRunId(WfRunId.newBuilder().setId(getWfRunId()).build())
            .setUserTaskGuid(getUserTaskRunGuid())
            .build()
        )
        .build();

        return req;
    }
}
