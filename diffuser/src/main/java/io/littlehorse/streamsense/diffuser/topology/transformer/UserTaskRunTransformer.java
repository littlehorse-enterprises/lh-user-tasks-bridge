package io.littlehorse.streamsense.diffuser.topology.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.littlehorse.sdk.common.proto.UserTaskRun;
import io.littlehorse.sdk.common.proto.VariableValue;
import io.littlehorse.streamsense.diffuser.topology.TimestampConverter;
import io.littlehorse.streamsense.diffuser.topology.VariableValueConverter;

import java.util.Map;

/**
 * Stateless transformer: UserTaskRun protobuf → JSON output.
 * Each UserTaskRun record is a full snapshot — no aggregation needed.
 *
 * <p>Output format:
 * <pre>
 * {
 *   "id": "<wfRunId>/<userTaskGuid>",
 *   "wfRunId": "<wfRunId>",
 *   "userTaskDefName": "<name>",
 *   "userTaskDefVersion": <version>,
 *   "status": "<status>",
 *   "userId": "<userId or null>",
 *   "userGroup": "<userGroup or null>",
 *   "results": { "<field>": <value> },
 *   "createdAt": "<ISO-8601>"
 * }
 * </pre>
 */
public class UserTaskRunTransformer {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static ObjectNode transform(UserTaskRun userTaskRun) {
        ObjectNode node = MAPPER.createObjectNode();

        String wfRunId = userTaskRun.getId().getWfRunId().getId();
        String userTaskGuid = userTaskRun.getId().getUserTaskGuid();

        node.put("id", "%s/%s".formatted(wfRunId, userTaskGuid));
        node.put("wfRunId", wfRunId);
        node.put("userTaskDefName", userTaskRun.getUserTaskDefId().getName());
        node.put("userTaskDefVersion", userTaskRun.getUserTaskDefId().getVersion());
        node.put("status", userTaskRun.getStatus().name());

        // userId: present if assigned to a specific user
        if (userTaskRun.hasUserId() && !userTaskRun.getUserId().isEmpty()) {
            node.put("userId", userTaskRun.getUserId());
        } else {
            node.putNull("userId");
        }

        // userGroup: present if assigned to a group
        if (userTaskRun.hasUserGroup() && !userTaskRun.getUserGroup().isEmpty()) {
            node.put("userGroup", userTaskRun.getUserGroup());
        } else {
            node.putNull("userGroup");
        }

        // results: map of field name -> value
        ObjectNode results = MAPPER.createObjectNode();
        for (Map.Entry<String, VariableValue> entry : userTaskRun.getResultsMap().entrySet()) {
            results.set(entry.getKey(), VariableValueConverter.convert(entry.getValue()));
        }
        node.set("results", results);

        node.put("createdAt", TimestampConverter.toIso8601(userTaskRun.getScheduledTime()));

        return node;
    }

    /** Extracts the UserTaskDef name used for topic routing. */
    public static String extractDefName(UserTaskRun userTaskRun) {
        return userTaskRun.getUserTaskDefId().getName();
    }
}
