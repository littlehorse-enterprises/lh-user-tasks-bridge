package io.littlehorse.streamsense.diffuser.topology.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.littlehorse.sdk.common.proto.ExternalEvent;
import io.littlehorse.streamsense.diffuser.topology.TimestampConverter;
import io.littlehorse.streamsense.diffuser.topology.VariableValueConverter;

/**
 * Stateless transformer: ExternalEvent protobuf → JSON output.
 *
 * <p>Output format:
 * <pre>
 * {
 *   "id": "<wfRunId>/<externalEventDefName>/<guid>",
 *   "wfRunId": "<wfRunId>",
 *   "content": <payload>,
 *   "claimed": <boolean>,
 *   "createdAt": "<ISO-8601>"
 * }
 * </pre>
 */
public class ExternalEventTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode transform(ExternalEvent event) {
        ObjectNode node = MAPPER.createObjectNode();

        String wfRunId = event.getId().getWfRunId().getId();
        String defName = event.getId().getExternalEventDefId().getName();
        String guid = event.getId().getGuid();

        node.put("id", "%s/%s/%s".formatted(wfRunId, defName, guid));
        node.put("wfRunId", wfRunId);
        node.set("content", VariableValueConverter.convert(event.getContent()));
        node.put("claimed", event.getClaimed());
        node.put("createdAt", TimestampConverter.toIso8601(event.getCreatedAt()));

        return node;
    }

    /** Extracts the ExternalEventDef name used for topic routing. */
    public static String extractDefName(ExternalEvent event) {
        return event.getId().getExternalEventDefId().getName();
    }
}
