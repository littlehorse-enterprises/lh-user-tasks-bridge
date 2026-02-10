package io.littlehorse.streamsense.diffuser.topology.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.littlehorse.sdk.common.proto.WorkflowEvent;
import io.littlehorse.streamsense.diffuser.topology.TimestampConverter;
import io.littlehorse.streamsense.diffuser.topology.VariableValueConverter;

/**
 * Stateless transformer: WorkflowEvent protobuf → JSON output.
 *
 * <p>Output format:
 * <pre>
 * {
 *   "id": "<wfRunId>/<workflowEventDefName>/<number>",
 *   "wfRunId": "<wfRunId>",
 *   "workflowEventDefName": "<name>",
 *   "content": <payload or null>,
 *   "createdAt": "<ISO-8601>"
 * }
 * </pre>
 */
public class WorkflowEventTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode transform(WorkflowEvent event) {
        ObjectNode node = MAPPER.createObjectNode();

        String wfRunId = event.getId().getWfRunId().getId();
        String defName = event.getId().getWorkflowEventDefId().getName();
        int number = event.getId().getNumber();

        node.put("id", "%s/%s/%d".formatted(wfRunId, defName, number));
        node.put("wfRunId", wfRunId);
        node.put("workflowEventDefName", defName);
        node.set("content", VariableValueConverter.convert(event.getContent()));
        node.put("createdAt", TimestampConverter.toIso8601(event.getCreatedAt()));

        return node;
    }

    public static String extractDefName(WorkflowEvent event) {
        return event.getId().getWorkflowEventDefId().getName();
    }
}
