package io.littlehorse.usertasks.util.enums;

public enum CustomUserIdClaim {
    EMAIL, PREFERRED_USERNAME, SUB;

    @Override
    public String toString() {
        return this.name().toLowerCase();
    }
}
