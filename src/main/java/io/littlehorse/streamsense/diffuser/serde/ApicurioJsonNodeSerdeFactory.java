package io.littlehorse.streamsense.diffuser.serde;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;

import java.util.Map;

public class ApicurioJsonNodeSerdeFactory {

    public static Serde<JsonNode> create(String registryUrl) {
        JsonSchemaKafkaSerializer<JsonNode> serializer = new JsonSchemaKafkaSerializer<>();
        JsonSchemaKafkaDeserializer<JsonNode> deserializer = new JsonSchemaKafkaDeserializer<>();

        Map<String, Object> config = Map.of(
                "apicurio.registry.url", registryUrl,
                "apicurio.registry.artifact-resolver-strategy",
                    "io.apicurio.registry.serde.strategy.TopicIdStrategy",
                "apicurio.registry.auto-register", "true",
                "apicurio.registry.deserializer.specific.java.type",
                    "com.fasterxml.jackson.databind.JsonNode"
        );

        serializer.configure(config, false);
        deserializer.configure(config, false);

        return Serdes.serdeFrom(serializer, deserializer);
    }
}
