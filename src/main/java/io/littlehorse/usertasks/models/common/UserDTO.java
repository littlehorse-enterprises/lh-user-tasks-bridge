package io.littlehorse.usertasks.models.common;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

/**
 * {@code UserDTO} is a Data Transfer Object that contains information about a specific User from an Identity Provider
 */
@Builder
@AllArgsConstructor
@Data
public class UserDTO {
    @NotBlank
    private String id;
    private String email;
    private String username;
}
