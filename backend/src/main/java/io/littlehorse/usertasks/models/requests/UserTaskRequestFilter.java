package io.littlehorse.usertasks.models.requests;

import static io.littlehorse.usertasks.util.DateUtil.localDateTimeToTimestamp;

import com.google.protobuf.Timestamp;
import io.littlehorse.usertasks.util.enums.UserTaskStatus;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.springframework.lang.Nullable;

/**
 * Class that is used to easily map the allowed filters when searching UserTaskRuns
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UserTaskRequestFilter {
    @Nullable
    private Timestamp earliestStartDate;

    @Nullable
    private Timestamp latestStartDate;

    @Nullable
    private UserTaskStatus status;

    @Nullable
    private String type;

    public static UserTaskRequestFilter buildUserTaskRequestFilter(
            @Nullable LocalDateTime earliestStartDate,
            @Nullable LocalDateTime latestStartDate,
            @Nullable UserTaskStatus status,
            @Nullable String type) {
        var actualEarliestDate =
                Objects.nonNull(earliestStartDate) ? localDateTimeToTimestamp(earliestStartDate) : null;

        var actualLatestDate = Objects.nonNull(latestStartDate) ? localDateTimeToTimestamp(latestStartDate) : null;

        return UserTaskRequestFilter.builder()
                .earliestStartDate(actualEarliestDate)
                .latestStartDate(actualLatestDate)
                .status(status)
                .type(type)
                .build();
    }
}
