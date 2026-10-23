package io.littlehorse.streamsense.diffuser.topology;

import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.streams.errors.DeserializationExceptionHandler;
import org.apache.kafka.streams.processor.ProcessorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * Logs deserialization errors and instructs Kafka Streams to skip the corrupt record
 * rather than shutting down the application.
 */
public class LogAndContinueDeserializationExceptionHandler implements DeserializationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LogAndContinueDeserializationExceptionHandler.class);

    @Override
    public DeserializationHandlerResponse handle(ProcessorContext context,
                                                  ConsumerRecord<byte[], byte[]> record,
                                                  Exception exception) {
        log.error("Deserialization error on topic={}, partition={}, offset={}. Skipping record.",
                record.topic(), record.partition(), record.offset(), exception);
        return DeserializationHandlerResponse.CONTINUE;
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration needed
    }
}
