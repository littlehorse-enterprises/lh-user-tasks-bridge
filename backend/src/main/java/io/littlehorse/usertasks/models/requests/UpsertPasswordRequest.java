package io.littlehorse.usertasks.models.requests;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class UpsertPasswordRequest {
    @NotBlank(message = "Password must not be NULL, empty nor a whitespace-only value")
    private String password;

    private boolean temporary;
}
