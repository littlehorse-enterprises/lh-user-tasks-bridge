package io.littlehorse.usertasks.models.common;

import io.littlehorse.sdk.common.proto.VariableType;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.usertasks.util.enums.UserTaskFieldType;
import io.swagger.v3.oas.annotations.media.Schema;
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
    @Schema(oneOf = {Double.class, String.class, Boolean.class, Integer.class})
    private Object value;

    public VariableValue toServerType() {

        switch (this.type) {
            case DOUBLE -> {
                return VariableValue.newBuilder()
                        .setDouble(Double.parseDouble(this.value.toString()))
                        .build();
            }
            case STRING -> {
                return VariableValue.newBuilder().setStr((String) this.value).build();
            }
            case BOOLEAN -> {
                return VariableValue.newBuilder().setBool((Boolean) this.value).build();
            }
            case INTEGER -> {
                return VariableValue.newBuilder()
                        .setInt(Integer.parseInt(this.value.toString()))
                        .build();
            }
            default -> throw new IllegalArgumentException("No matching VariableValue found");
        }
    }

    public static UserTaskVariableValue fromServerType(VariableValue serverVariableValue) {
        if (serverVariableValue.hasBool()) {
            return UserTaskVariableValue.builder()
                    .value(serverVariableValue.getBool())
                    .type(UserTaskFieldType.fromServerType(VariableType.BOOL))
                    .build();
        } else if (serverVariableValue.hasDouble()) {
            return UserTaskVariableValue.builder()
                    .value(serverVariableValue.getDouble())
                    .type(UserTaskFieldType.fromServerType(VariableType.DOUBLE))
                    .build();
        } else if (serverVariableValue.hasInt()) {
            return UserTaskVariableValue.builder()
                    .value(serverVariableValue.getInt())
                    .type(UserTaskFieldType.fromServerType(VariableType.INT))
                    .build();
        } else if (serverVariableValue.hasStr()) {
            return UserTaskVariableValue.builder()
                    .value(serverVariableValue.getStr())
                    .type(UserTaskFieldType.fromServerType(VariableType.STR))
                    .build();
        } else {
            throw new IllegalArgumentException("Unknown VariableValue!");
        }
    }
}
