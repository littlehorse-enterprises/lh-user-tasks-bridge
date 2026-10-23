package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.NullNode;
import io.littlehorse.sdk.common.proto.VariableValue;

import java.util.Base64;

/**
 * Converts a protobuf {@link VariableValue} to a Jackson {@link JsonNode}.
 *
 * <p>Mapping:
 * <ul>
 *   <li>{@code str} → TextNode</li>
 *   <li>{@code int} → LongNode</li>
 *   <li>{@code double} → DoubleNode</li>
 *   <li>{@code bool} → BooleanNode</li>
 *   <li>{@code json_obj} → parsed ObjectNode</li>
 *   <li>{@code json_arr} → parsed ArrayNode</li>
 *   <li>{@code bytes} → TextNode (base64)</li>
 *   <li>{@code wf_run_id} → TextNode (WfRunId.id)</li>
 *   <li>{@code utc_timestamp} → TextNode (ISO-8601)</li>
 *   <li>unset → NullNode</li>
 * </ul>
 */
public class VariableValueConverter {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static JsonNode convert(VariableValue value) {
        if (value == null) {
            return NullNode.getInstance();
        }

        return switch (value.getValueCase()) {
            case STR -> MAPPER.getNodeFactory().textNode(value.getStr());
            case INT -> MAPPER.getNodeFactory().numberNode(value.getInt());
            case DOUBLE -> MAPPER.getNodeFactory().numberNode(value.getDouble());
            case BOOL -> MAPPER.getNodeFactory().booleanNode(value.getBool());
            case JSON_OBJ -> parseJson(value.getJsonObj());
            case JSON_ARR -> parseJson(value.getJsonArr());
            case BYTES -> MAPPER.getNodeFactory().textNode(
                    Base64.getEncoder().encodeToString(value.getBytes().toByteArray()));
            case WF_RUN_ID -> MAPPER.getNodeFactory().textNode(value.getWfRunId().getId());
            case UTC_TIMESTAMP -> MAPPER.getNodeFactory().textNode(
                    TimestampConverter.toIso8601(value.getUtcTimestamp()));
            case VALUE_NOT_SET -> NullNode.getInstance();
            default -> NullNode.getInstance();
        };
    }

    private static JsonNode parseJson(String jsonString) {
        try {
            return MAPPER.readTree(jsonString);
        } catch (Exception e) {
            // If parsing fails, return as a plain text node
            return MAPPER.getNodeFactory().textNode(jsonString);
        }
    }
}
