package io.littlehorse.streamsense.diffuser.serde;

import io.littlehorse.sdk.common.proto.MetadataOutputTopicRecord;
import io.littlehorse.sdk.common.proto.OutputTopicRecord;
import org.apache.kafka.common.serialization.Deserializer;
import org.apache.kafka.common.serialization.Serde;
import org.apache.kafka.common.serialization.Serdes;
import org.apache.kafka.common.serialization.Serializer;

public class ProtobufSerdes {

    public static Serde<OutputTopicRecord> outputTopicRecord() {
        Serializer<OutputTopicRecord> serializer = (topic, data) ->
                data == null ? null : data.toByteArray();

        Deserializer<OutputTopicRecord> deserializer = (topic, bytes) -> {
            if (bytes == null) return null;
            try {
                return OutputTopicRecord.parseFrom(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize OutputTopicRecord", e);
            }
        };

        return Serdes.serdeFrom(serializer, deserializer);
    }

    public static Serde<MetadataOutputTopicRecord> metadataOutputTopicRecord() {
        Serializer<MetadataOutputTopicRecord> serializer = (topic, data) ->
                data == null ? null : data.toByteArray();

        Deserializer<MetadataOutputTopicRecord> deserializer = (topic, bytes) -> {
            if (bytes == null) return null;
            try {
                return MetadataOutputTopicRecord.parseFrom(bytes);
            } catch (Exception e) {
                throw new RuntimeException("Failed to deserialize MetadataOutputTopicRecord", e);
            }
        };

        return Serdes.serdeFrom(serializer, deserializer);
    }
}
