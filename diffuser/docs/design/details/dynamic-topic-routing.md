# Dynamic Topic Routing with Kafka Streams & Apicurio JSON Schema SerDes

## Overview

Route Kafka Streams records to dynamically resolved sink topics at runtime using record headers. Schemas are pre-registered in Apicurio Registry per target topic.

## Key Design Decisions

- **Value type**: `com.fasterxml.jackson.databind.JsonNode` — schema-agnostic at the Java level, but validated against the correct JSON Schema in Apicurio per topic.
- **Topic resolution**: Via `TopicNameExtractor`, which extracts the target topic from the record header `streamlet-topic`.
- **Schema resolution**: `TopicIdStrategy` — Apicurio looks up the schema artifact as `<topic>-value` in the registry. Since `TopicNameExtractor` resolves the topic *before* serialization, the serializer receives the correct dynamic topic name automatically.
- **No upfront topic declaration needed**: Kafka Streams produces to whatever topic the extractor returns.

## Header Contract

Each record MUST carry a header named `streamlet-topic` whose UTF-8 value is the target topic name.

| Header            | Type   | Required | Description          |
|--------------------|--------|----------|----------------------|
| `streamlet-topic` | String | Yes      | Target sink topic name |

## Dependencies (Gradle Kotlin DSL)

```kotlin
// Jackson
implementation("com.fasterxml.jackson.core:jackson-databind:2.17.2")

// Apicurio Registry SerDe
implementation("io.apicurio:apicurio-registry-serdes-jsonschema-serde:2.6.4.Final")
```

Kafka Streams and related Kafka dependencies are assumed to be provided separately.

## Apicurio Registry Configuration

| Property | Value | Description |
|---|---|---|
| `apicurio.registry.url` | `http://<host>:<port>/apis/registry/v2` | Registry API base URL |
| `apicurio.registry.artifact-resolver-strategy` | `io.apicurio.registry.serde.strategy.TopicIdStrategy` | Resolves artifact ID as `<topic>-value` |
| `apicurio.registry.deserializer.specific.java.type` | `com.fasterxml.jackson.databind.JsonNode` | Deserializer target type |

## Implementation

### TopicNameExtractor

Extracts the target topic from the `streamlet-topic` header on the `RecordContext`. The header is available in the `TopicNameExtractor` — no preceding transform step needed.

```java
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
```

### Apicurio JsonNode Serde Factory

Creates a `Serde<JsonNode>` backed by Apicurio's JSON Schema serializer/deserializer with `TopicIdStrategy`.

```java
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
            "apicurio.registry.deserializer.specific.java.type",
                "com.fasterxml.jackson.databind.JsonNode"
        );

        serializer.configure(config, false);
        deserializer.configure(config, false);

        return Serdes.serdeFrom(serializer, deserializer);
    }
}
```

### Topology Wiring

```java
import com.fasterxml.jackson.databind.JsonNode;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.streams.StreamsBuilder;
import org.apache.kafka.streams.kstream.Consumed;
import org.apache.kafka.streams.kstream.KStream;
import org.apache.kafka.streams.kstream.Produced;

public class DynamicTopicRoutingTopology {

    public void build(StreamsBuilder builder, String inputTopic, String registryUrl) {

        Serde<JsonNode> valueSerde = ApicurioJsonNodeSerdeFactory.create(registryUrl);

        KStream<String, JsonNode> stream = builder.stream(
            inputTopic,
            Consumed.with(Serdes.String(), valueSerde)
        );

        stream.to(
            (key, value, recordContext) -> StreamletTopicExtractor.extract(recordContext),
            Produced.with(Serdes.String(), valueSerde)
        );
    }
}
```

## How It Works (Serialization Lifecycle)

1. A record arrives on the input topic and is deserialized using the configured Serde.
2. `TopicNameExtractor` is invoked with `(key, value, recordContext)` to resolve the sink topic name from the `streamlet-topic` header.
3. The resolved topic name is passed to the serializer as the `topic` argument in `serialize(topic, data)`.
4. Apicurio's `TopicIdStrategy` uses this topic name to look up the schema artifact as `<topic>-value` in the registry.
5. The record is serialized against the resolved schema and produced to the dynamic topic.

## Notes

- If input and sink topics use different schema resolution strategies, create separate Serde instances for `Consumed` and `Produced`.
- Schemas must be pre-registered in Apicurio under the artifact ID `<target-topic>-value` for each possible sink topic.
- The `TopicNameExtractor` approach does not require upfront knowledge of sink topics — new topics can be introduced at runtime as long as their schemas are registered.
