package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.kafka.common.serialization.Serializer;

/**
 * Simple JSON serializer for Jackson JsonNode.
 * Used as the sink serializer when Apicurio is not available (e.g. in tests).
 */
public class JsonNodeSerializer implements Serializer<JsonNode> {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Override
    public byte[] serialize(String topic, JsonNode data) {
        if (data == null) return null;
        try {
            return MAPPER.writeValueAsBytes(data);
        } catch (Exception e) {
            throw new RuntimeException("Failed to serialize JsonNode", e);
        }
    }
}
