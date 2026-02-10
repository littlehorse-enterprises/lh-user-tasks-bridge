package io.littlehorse.streamsense.diffuser.topology;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.RecordContext;

import java.nio.charset.StandardCharsets;

public class StreamletTopicExtractor {

    public static final String STREAMLET_TOPIC_HEADER = "streamlet-topic";

    public static String extract(RecordContext recordContext) {
        Header header = recordContext.headers().lastHeader(STREAMLET_TOPIC_HEADER);
        if (header == null) {
            throw new IllegalStateException(
                    "Record missing required header: " + STREAMLET_TOPIC_HEADER);
        }
        return new String(header.value(), StandardCharsets.UTF_8);
    }
}
