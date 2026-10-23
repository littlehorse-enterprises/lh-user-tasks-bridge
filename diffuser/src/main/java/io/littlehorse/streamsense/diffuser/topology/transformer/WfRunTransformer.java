package io.littlehorse.streamsense.diffuser.topology.transformer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.littlehorse.sdk.common.proto.Variable;
import io.littlehorse.sdk.common.proto.WfRun;
import io.littlehorse.streamsense.diffuser.topology.TimestampConverter;
import io.littlehorse.streamsense.diffuser.topology.VariableValueConverter;

/**
 * Transforms WfRun and Variable protobuf records into the WfSpec Streamlet JSON format.
 * Stateful: maintains and merges state across multiple records for the same wfRunId.
 *
 * <p>Output format:
 * <pre>
 * {
 *   "id": "<wfRunId>",
 *   "status": "<status>",
 *   "columns": { "<var-name>": <value> },
 *   "createdAt": "<ISO-8601>",
 *   "updatedAt": "<ISO-8601>"
 * }
 * </pre>
 */
public class WfRunTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    /**
     * Creates or updates state from a WfRun record.
     *
     * @param wfRun the WfRun protobuf record
     * @param existingState existing state (null if first record for this wfRunId)
     * @param recordTimestamp the OutputTopicRecord timestamp
     * @return updated state as ObjectNode
     */
    public static ObjectNode applyWfRun(WfRun wfRun,
                                         ObjectNode existingState,
                                         com.google.protobuf.Timestamp recordTimestamp) {
        ObjectNode state = existingState != null ? existingState : MAPPER.createObjectNode();

        state.put("id", wfRun.getId().getId());
        state.put("status", wfRun.getStatus().name());

        if (!state.has("columns")) {
            state.set("columns", MAPPER.createObjectNode());
        }

        if (wfRun.hasStartTime()) {
            state.put("createdAt", TimestampConverter.toIso8601(wfRun.getStartTime()));
        }

        state.put("updatedAt", TimestampConverter.toIso8601(recordTimestamp));

        return state;
    }

    /**
     * Updates state from a Variable record.
     *
     * @param variable the Variable protobuf record
     * @param existingState existing state (null if first record — creates skeleton)
     * @param wfRunId the wfRunId (from Kafka record key)
     * @param recordTimestamp the OutputTopicRecord timestamp
     * @return updated state as ObjectNode
     */
    public static ObjectNode applyVariable(Variable variable,
                                            ObjectNode existingState,
                                            String wfRunId,
                                            com.google.protobuf.Timestamp recordTimestamp) {
        ObjectNode state = existingState != null ? existingState : MAPPER.createObjectNode();

        if (!state.has("id")) {
            state.put("id", wfRunId);
        }
        if (!state.has("columns")) {
            state.set("columns", MAPPER.createObjectNode());
        }

        String varName = variable.getId().getName();
        JsonNode value = VariableValueConverter.convert(variable.getValue());
        ((ObjectNode) state.get("columns")).set(varName, value);

        state.put("updatedAt", TimestampConverter.toIso8601(recordTimestamp));

        return state;
    }
}
