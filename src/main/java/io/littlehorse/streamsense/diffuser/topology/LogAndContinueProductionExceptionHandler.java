package io.littlehorse.streamsense.diffuser.topology;

import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.streams.errors.ProductionExceptionHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

public class LogAndContinueProductionExceptionHandler implements ProductionExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(LogAndContinueProductionExceptionHandler.class);

    @Override
    public ProductionExceptionHandlerResponse handle(ProducerRecord<byte[], byte[]> record, Exception exception) {
        log.error("Production error on topic={}, partition={}. Skipping record.",
                record.topic(), record.partition(), exception);
        return ProductionExceptionHandlerResponse.CONTINUE;
    }

    @Override
    public void configure(Map<String, ?> configs) {
        // No configuration needed
    }
}
