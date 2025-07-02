package io.littlehorse.usertasks.models.responses;

import com.fasterxml.jackson.annotation.JsonFormat;
import io.littlehorse.sdk.common.proto.TaskRunId;
import io.littlehorse.sdk.common.proto.UserTaskEvent;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.UserTaskRunId;
import io.littlehorse.usertasks.util.DateUtil;
import io.littlehorse.usertasks.util.enums.UserTaskEventType;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NonNull;

import java.time.LocalDateTime;

/**
 * {@code AuditEventDTO} is a Data Transfer Object that contains information about a specific {@code io.littlehorse.sdk.common.proto.UserTaskEvent}
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskEvent
 */
@Data
@Builder
@AllArgsConstructor
public class AuditEventDTO {
    @NotNull
    @JsonFormat(pattern= "yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
    private LocalDateTime time;
    @NotNull
    @Schema(oneOf = {UserTaskExecutedEvent.class, UserTaskAssignedEvent.class, UserTaskCancelledEvent.class})
    private IUserTaskEvent event;
    @NotNull
    private UserTaskEventType type;

    @NotNull
    public static AuditEventDTO fromUserTaskEvent(@NonNull UserTaskEvent serverEvent) {
        final IUserTaskEvent parsedEvent;
        final UserTaskEventType eventType;

        if (serverEvent.hasTaskExecuted()) {
            parsedEvent = UserTaskExecutedEvent.parseFromServer(serverEvent.getTaskExecuted());
            eventType = UserTaskEventType.TASK_EXECUTED;
        } else if (serverEvent.hasAssigned()) {
            parsedEvent = UserTaskAssignedEvent.parseFromServer(serverEvent.getAssigned());
            eventType = UserTaskEventType.TASK_ASSIGNED;
        } else if (serverEvent.hasCancelled()) {
            parsedEvent = UserTaskCancelledEvent.parseFromServer(serverEvent.getCancelled());
            eventType = UserTaskEventType.TASK_CANCELLED;
        } else if (serverEvent.hasCommentAdded()){
            parsedEvent = UserTaskRunCommentEvent.parserFromserver(serverEvent.getCommentAdded());
            eventType = UserTaskEventType.COMMENTED;
        } else if (serverEvent.hasCommentEdited()){
            parsedEvent = UserTaskRunCommentEvent.parserFromserver(serverEvent.getCommentEdited());
            eventType = UserTaskEventType.COMMENT_EDITED;
        } else if (serverEvent.hasCommentDeleted()){
            parsedEvent = DeleteUserTaskRunCommentEvent.parserFromserver(serverEvent.getCommentDeleted());
            eventType = UserTaskEventType.COMMENT_DELETED;
        }
        else {
            throw new IllegalArgumentException("Unknown audit event.");
        }

        return AuditEventDTO.builder()
                .time(DateUtil.timestampToLocalDateTime(serverEvent.getTime()))
                .event(parsedEvent)
                .type(eventType)
                .build();
    }

    @Data
    @Builder
    @AllArgsConstructor
    static class UserTaskExecutedEvent implements IUserTaskEvent {
        @NotBlank
        private String wfRunId;
        @NotBlank
        private String userTaskGuid;

        static UserTaskExecutedEvent parseFromServer(@NonNull UserTaskEvent.UTETaskExecuted serverObject) {
            TaskRunId taskRun = serverObject.getTaskRun();
            return UserTaskExecutedEvent.builder()
                    .wfRunId(taskRun.getWfRunId().getId())
                    .userTaskGuid(taskRun.getTaskGuid())
                    .build();
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    static class UserTaskAssignedEvent implements IUserTaskEvent {
        private String oldUserId;
        private String oldUserGroup;
        private String newUserId;
        private String newUserGroup;

        static UserTaskAssignedEvent parseFromServer(@NonNull UserTaskEvent.UTEAssigned serverObject) {
            return UserTaskAssignedEvent.builder()
                    .oldUserId(serverObject.getOldUserId())
                    .oldUserGroup(serverObject.getOldUserGroup())
                    .newUserId(serverObject.getNewUserId())
                    .newUserGroup(serverObject.getNewUserGroup())
                    .build();
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    static class UserTaskCancelledEvent implements IUserTaskEvent {
        @NotBlank
        private String message;

        static UserTaskCancelledEvent parseFromServer(@NonNull UserTaskEvent.UTECancelled serverObject) {
            return UserTaskCancelledEvent.builder()
                    .message(serverObject.getMessage())
                    .build();
        }
    }
    
    @Data
    @Builder
    @AllArgsConstructor
    static class UserTaskRunCommentEvent implements IUserTaskEvent{

        private String comment;
        private String userId;
        private int commentId;

        static UserTaskRunCommentEvent parserFromserver(@NonNull UserTaskEvent.UTECommented  serverObject){

            return UserTaskRunCommentEvent.builder()
            .comment(serverObject.getComment())
            .commentId(serverObject.getUserCommentId())
            .userId(serverObject.getUserId())
            .build();
        }
    }

    @Data
    @Builder
    @AllArgsConstructor
    static class DeleteUserTaskRunCommentEvent implements IUserTaskEvent{

        private String userId;
        private int commentId;

        static DeleteUserTaskRunCommentEvent parserFromserver(@NonNull UserTaskEvent.UTECommentDeleted  serverObject){

            return DeleteUserTaskRunCommentEvent.builder()
            .commentId(serverObject.getUserCommentId())
            .userId(serverObject.getUserId())
            .build();
        }
    }
}
