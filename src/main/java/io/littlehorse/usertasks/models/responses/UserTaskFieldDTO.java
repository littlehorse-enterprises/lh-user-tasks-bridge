package io.littlehorse.usertasks.models.responses;

import io.littlehorse.sdk.common.proto.UserTaskField;
import io.littlehorse.usertasks.util.UserTaskFieldType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * {@code UserTaskFieldDTO} is a Data Transfer Object that contains information about a specific {@code io.littlehorse.sdk.common.proto.UserTaskField}
 *
 * @see io.littlehorse.sdk.common.proto.UserTaskField
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskFieldDTO {
    private String name;
    private String displayName;
    private String description;
    private UserTaskFieldType type;
    private boolean required;

    public static UserTaskFieldDTO fromServerUserTaskField(UserTaskField userTaskField) {
        return UserTaskFieldDTO.builder()
                .name(userTaskField.getName())
                .displayName(userTaskField.getDisplayName())
                .description(userTaskField.getDescription())
                .type(UserTaskFieldType.fromServerType(userTaskField.getType()))
                .required(userTaskField.getRequired())
                .build();
    }
}
