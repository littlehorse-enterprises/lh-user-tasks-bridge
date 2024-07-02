package io.littlehorse.usertasks.models.requests;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

@Data
@Builder
@AllArgsConstructor
public class AssignationRequest {
    private String userId;
    private String userGroup;
}
