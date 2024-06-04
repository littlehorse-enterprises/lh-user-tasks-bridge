package io.littlehorse.usertasks.util;

import io.littlehorse.sdk.common.proto.VariableType;

/**
 * {@code UserTaskFieldType} is a utility {@code enum} that represents the different data types  that
 * a {@code io.littlehorse.sdk.common.proto.UserTaskField} can be.
 *
 * @see io.littlehorse.sdk.common.proto.VariableType
 */
public enum UserTaskFieldType {
    DOUBLE, BOOLEAN, STRING, INTEGER, UNRECOGNIZED;

    /**
     * Converts a {@code io.littlehorse.sdk.common.proto.VariableType} to a {@code UserTaskFieldType}
     *
     * @param type {@code io.littlehorse.sdk.common.proto.VariableType} gotten from LittleHorse server
     * @return A custom representation of an {@code io.littlehorse.sdk.common.proto.VariableType}
     * @see io.littlehorse.sdk.common.proto.VariableType
     */
    public static UserTaskFieldType fromServerType(VariableType type) {
        switch (type) {
            case DOUBLE -> {
                return DOUBLE;
            }
            case BOOL -> {
                return BOOLEAN;
            }
            case STR -> {
                return STRING;
            }
            case INT -> {
                return INTEGER;
            }
            default -> {
                return UNRECOGNIZED;
            }
        }
    }
}
