package io.littlehorse.usertasks.models.requests;

import com.google.protobuf.Timestamp;
import io.littlehorse.usertasks.util.UserTaskStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskRequestFilter {
    @Nullable
    private Timestamp createdTime;
    @Nullable
    private UserTaskStatus status;
    @Nullable
    private String type;
}
