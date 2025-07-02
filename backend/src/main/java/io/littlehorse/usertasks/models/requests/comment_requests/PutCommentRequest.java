package io.littlehorse.usertasks.models.requests.comment_requests;

import io.littlehorse.sdk.common.proto.PutUserTaskRunCommentRequest;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.sdk.common.proto.WfRunId;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class PutCommentRequest {

    private String wfRunId;

    private String userTaskRunGuid;

    private String comment;


    public PutUserTaskRunCommentRequest toServer(String userId){
        PutUserTaskRunCommentRequest req = PutUserTaskRunCommentRequest.newBuilder()
        .setComment(getComment())
        .setUserId(userId)
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
