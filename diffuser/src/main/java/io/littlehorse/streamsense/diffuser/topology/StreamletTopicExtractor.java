package io.littlehorse.streamsense.diffuser.topology;

import org.apache.kafka.common.header.Header;
import org.apache.kafka.streams.processor.RecordContext;

import java.nio.charset.StandardCharsets;

/**
 * Extracts the target sink topic name from the {@code streamlet-topic} record header.
 * Used as a {@link org.apache.kafka.streams.processor.TopicNameExtractor} in the
 * dynamic-routing sink so each record is produced to the correct streamlet topic.
 */
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
