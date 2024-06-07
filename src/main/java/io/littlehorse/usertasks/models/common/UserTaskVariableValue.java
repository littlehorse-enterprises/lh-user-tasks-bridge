package io.littlehorse.usertasks.models.common;

import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.usertasks.util.UserTaskFieldType;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Slf4j
public class UserTaskVariableValue {
    @NotNull
    private UserTaskFieldType type;
    @NotNull
    private Object value;

    public VariableValue toServerType() {

        switch (this.type) {
            case DOUBLE -> {
                return VariableValue.newBuilder()
                        .setDouble((Double) this.value)
                        .build();
            }
            case STRING -> {
                return VariableValue.newBuilder()
                        .setStr((String) this.value)
                        .build();
            }
            case BOOLEAN -> {
                return VariableValue.newBuilder()
                        .setBool((Boolean) this.value)
                        .build();
            }
            case INTEGER -> {
                return VariableValue.newBuilder()
                        .setInt((Integer) this.value)
                        .build();
            }
            default -> throw new IllegalArgumentException("No matching VariableValue found");
        }
    }
}
