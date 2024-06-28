package io.littlehorse.usertasks.models.requests;

import io.littlehorse.usertasks.util.enums.UserTaskAssignationType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AssignationRequest {
    @NotNull
    private UserTaskAssignationType type;
    @NotBlank
    private String assignee;
}
