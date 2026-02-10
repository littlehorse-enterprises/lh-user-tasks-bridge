package io.littlehorse.streamsense.diffuser.topology;

import com.google.protobuf.Timestamp;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;

/**
 * Converts protobuf {@link Timestamp} to ISO-8601 UTC strings.
 */
public class TimestampConverter {

    private static final DateTimeFormatter ISO_FORMATTER =
            DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
                    .withZone(ZoneOffset.UTC);

    public static String toIso8601(Timestamp timestamp) {
        if (timestamp == null || (timestamp.getSeconds() == 0 && timestamp.getNanos() == 0)) {
            return null;
        }
        Instant instant = Instant.ofEpochSecond(timestamp.getSeconds(), timestamp.getNanos());
        return ISO_FORMATTER.format(instant);
    }
}
