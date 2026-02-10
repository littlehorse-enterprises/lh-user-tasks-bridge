package io.littlehorse.streamsense.diffuser.topology;

import com.google.protobuf.Timestamp;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class TimestampConverterTest {

    @Test
    void convertsTimestampToIso8601() {
        // 2026-02-10T08:04:33.531Z  (1770710673 epoch seconds)
        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(1770710673)
                .setNanos(531000000)
                .build();

        String result = TimestampConverter.toIso8601(ts);
        assertEquals("2026-02-10T08:04:33.531Z", result);
    }

    @Test
    void returnsNullForNullTimestamp() {
        assertNull(TimestampConverter.toIso8601(null));
    }

    @Test
    void returnsNullForZeroTimestamp() {
        Timestamp ts = Timestamp.newBuilder().setSeconds(0).setNanos(0).build();
        assertNull(TimestampConverter.toIso8601(ts));
    }
}
