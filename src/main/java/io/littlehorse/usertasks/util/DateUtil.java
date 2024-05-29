package io.littlehorse.usertasks.util;

import com.google.protobuf.Timestamp;
import org.springframework.lang.NonNull;

import java.time.LocalDateTime;
import java.time.ZoneOffset;

public class DateUtil {
    public static boolean isDateRangeValid(@NonNull Timestamp earliestDate, @NonNull Timestamp latestDate) {
        var parsedEarliestDate = timestampToLocalDateTime(earliestDate);
        var parsedLatestDate = timestampToLocalDateTime(latestDate);

        return parsedEarliestDate.isBefore(parsedLatestDate);
    }

    public static LocalDateTime timestampToLocalDateTime(@NonNull Timestamp date) {
        return LocalDateTime.ofEpochSecond(date.getSeconds(), date.getNanos(), ZoneOffset.UTC);
    }
}
