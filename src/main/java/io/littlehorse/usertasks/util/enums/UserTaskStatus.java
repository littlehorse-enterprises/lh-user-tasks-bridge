package io.littlehorse.usertasks.util.enums;

import io.littlehorse.sdk.common.proto.UserTaskRunStatus;
import lombok.NonNull;

/**
 * {@code UserTaskStatus} is a utility {@code enum} that represents the different statuses that
 * a {@code io.littlehorse.sdk.common.proto.UserTaskRun} can transition to.
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskRunStatus
 */
public enum UserTaskStatus {
    UNASSIGNED, ASSIGNED, DONE, CANCELLED;

    /**
     * Converts an {@code io.littlehorse.sdk.common.proto.UserTaskRunStatus} to an {@code UserTaskStatus}
     *
     * @param serverStatus {@code io.littlehorse.sdk.common.proto.UserTaskRunStatus} gotten from LittleHorse server
     * @return A custom representation of an {@code io.littlehorse.sdk.common.proto.UserTaskRunStatus}
     */
    public static UserTaskStatus fromServerStatus(@NonNull UserTaskRunStatus serverStatus) {
        switch (serverStatus) {
            case UNASSIGNED -> {
                return UNASSIGNED;
            }
            case ASSIGNED -> {
                return ASSIGNED;
            }
            case DONE -> {
                return DONE;
            }
            case CANCELLED -> {
                return CANCELLED;
            }

            default -> throw new IllegalArgumentException("Unknown UserTaskRunStatus gotten from server");
        }
    }

    public UserTaskRunStatus toServerStatus() {
        switch (this) {
            case UNASSIGNED -> {
                return UserTaskRunStatus.UNASSIGNED;
            }
            case ASSIGNED -> {
                return UserTaskRunStatus.ASSIGNED;
            }
            case DONE -> {
                return UserTaskRunStatus.DONE;
            }
            case CANCELLED -> {
                return UserTaskRunStatus.CANCELLED;
            }

            default -> throw new IllegalArgumentException("No matching UserTaskRunStatus found");
        }
    }
}
