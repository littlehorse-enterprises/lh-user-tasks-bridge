package io.littlehorse.usertasks.models.responses;

import io.littlehorse.sdk.common.proto.UserTaskField;
import io.littlehorse.usertasks.util.enums.UserTaskFieldType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.NonNull;

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
    @NotBlank
    private String name;
    @NotBlank
    private String displayName;
    private String description;
    @NotNull
    private UserTaskFieldType type;
    private boolean required;

    public static UserTaskFieldDTO fromServerUserTaskField(@NonNull UserTaskField userTaskField) {
        return UserTaskFieldDTO.builder()
                .name(userTaskField.getName())
                .displayName(userTaskField.getDisplayName())
                .description(userTaskField.getDescription())
                .type(UserTaskFieldType.fromServerType(userTaskField.getType()))
                .required(userTaskField.getRequired())
                .build();
    }
}
