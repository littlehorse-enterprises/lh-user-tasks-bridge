package io.littlehorse.streamsense.diffuser.serde;

import com.fasterxml.jackson.databind.JsonNode;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaDeserializer;
import io.apicurio.registry.serde.jsonschema.JsonSchemaKafkaSerializer;
import org.apache.kafka.common.header.Headers;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serializer;

import java.util.HashMap;
import java.util.Map;

/**
 * Creates a {@link Serde} for {@link JsonNode} backed by Apicurio Registry JSON Schema
 * serializer/deserializer. Uses {@code TopicIdStrategy} so the schema artifact is resolved
 * as {@code <topic>-value} from the registry.
 *
 * <p>The returned {@link Serde}, {@link Serializer}, and {@link Deserializer} all ignore
 * subsequent {@code configure()} calls. This is necessary because Kafka Streams calls
 * {@code configure()} on both the serde and the extracted serializer during sink-node
 * initialization ({@code SinkNode.init()} via {@code WrappingNullableUtils}) with only
 * standard Kafka properties, which would overwrite the Apicurio-specific settings
 * (registry URL, artifact-resolver-strategy, etc.).
 */
public class ApicurioJsonNodeSerdeFactory {

    /**
     * @param configOverrides Apicurio configuration properties with {@code apicurio.registry.*}
     *                        prefixed keys. Must contain at least {@code apicurio.registry.url}.
     *                        Overrides are merged on top of the hardcoded defaults.
     */
    public static Serde<JsonNode> create(Map<String, Object> configOverrides) {
        JsonSchemaKafkaSerializer<JsonNode> serializer = new JsonSchemaKafkaSerializer<>();
        JsonSchemaKafkaDeserializer<JsonNode> deserializer = new JsonSchemaKafkaDeserializer<>();

        Map<String, Object> config = new HashMap<>(Map.of(
                "apicurio.registry.artifact-resolver-strategy",
                    "io.apicurio.registry.serde.strategy.TopicIdStrategy",
                "apicurio.registry.auto-register", "true",
                "apicurio.registry.deserializer.specific.java.type",
                    "com.fasterxml.jackson.databind.JsonNode"
        ));
        config.putAll(configOverrides);

        serializer.configure(config, false);
        deserializer.configure(config, false);

        // Wrap the serializer and deserializer so that configure() is a no-op.
        // The Serializer/Deserializer interfaces have default no-op configure(),
        // so the wrappers inherit it and only delegate serialize/deserialize/close.
        Serializer<JsonNode> safeSerializer = new Serializer<>() {
            @Override
            public byte[] serialize(String topic, JsonNode data) {
                return serializer.serialize(topic, data);
            }

            @Override
            public byte[] serialize(String topic, Headers headers, JsonNode data) {
                return serializer.serialize(topic, headers, data);
            }

            @Override
            public void close() {
                serializer.close();
            }
        };

        Deserializer<JsonNode> safeDeserializer = new Deserializer<>() {
            @Override
            public JsonNode deserialize(String topic, byte[] data) {
                return deserializer.deserialize(topic, data);
            }

            @Override
            public JsonNode deserialize(String topic, Headers headers, byte[] data) {
                return deserializer.deserialize(topic, headers, data);
            }

            @Override
            public void close() {
                deserializer.close();
            }
        };

        return new Serde<>() {
            @Override
            public Serializer<JsonNode> serializer() {
                return safeSerializer;
            }

            @Override
            public Deserializer<JsonNode> deserializer() {
                return safeDeserializer;
            }
        };
    }
}
