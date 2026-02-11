package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.google.protobuf.ByteString;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.sdk.common.proto.WfRunId;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class VariableValueConverterTest {

    @Test
    void convertsString() {
        VariableValue value = VariableValue.newBuilder().setStr("hello").build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isTextual());
        assertEquals("hello", result.asText());
    }

    @Test
    void convertsInt() {
        VariableValue value = VariableValue.newBuilder().setInt(42).build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isNumber());
        assertEquals(42L, result.asLong());
    }

    @Test
    void convertsDouble() {
        VariableValue value = VariableValue.newBuilder().setDouble(3.14).build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isNumber());
        assertEquals(3.14, result.asDouble(), 0.001);
    }

    @Test
    void convertsBool() {
        VariableValue value = VariableValue.newBuilder().setBool(true).build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isBoolean());
        assertTrue(result.asBoolean());
    }

    @Test
    void convertsJsonObj() {
        VariableValue value = VariableValue.newBuilder()
                .setJsonObj("{\"key\":\"value\"}").build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isObject());
        assertEquals("value", result.get("key").asText());
    }

    @Test
    void convertsJsonArr() {
        VariableValue value = VariableValue.newBuilder()
                .setJsonArr("[1,2,3]").build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isArray());
        assertEquals(3, result.size());
    }

    @Test
    void convertsBytes() {
        VariableValue value = VariableValue.newBuilder()
                .setBytes(ByteString.copyFromUtf8("test")).build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isTextual());
        // Base64 of "test"
        assertEquals("dGVzdA==", result.asText());
    }

    @Test
    void convertsWfRunId() {
        VariableValue value = VariableValue.newBuilder()
                .setWfRunId(WfRunId.newBuilder().setId("abc123").build()).build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isTextual());
        assertEquals("abc123", result.asText());
    }

    @Test
    void convertsTimestamp() {
        Timestamp ts = Timestamp.newBuilder()
                .setSeconds(1770710673)
                .setNanos(531000000)
                .build();
        VariableValue value = VariableValue.newBuilder().setUtcTimestamp(ts).build();
        JsonNode result = VariableValueConverter.convert(value);
        assertTrue(result.isTextual());
        assertEquals("2026-02-10T08:04:33.531Z", result.asText());
    }

    @Test
    void convertsNull() {
        JsonNode result = VariableValueConverter.convert(null);
        assertSame(NullNode.getInstance(), result);
    }

    @Test
    void convertsUnsetValue() {
        VariableValue value = VariableValue.newBuilder().build();
        JsonNode result = VariableValueConverter.convert(value);
        assertSame(NullNode.getInstance(), result);
    }
}
