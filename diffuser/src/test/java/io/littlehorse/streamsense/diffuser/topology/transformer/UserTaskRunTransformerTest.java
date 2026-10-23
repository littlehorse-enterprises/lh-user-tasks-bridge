package io.littlehorse.streamsense.diffuser.topology.transformer;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class UserTaskRunTransformerTest {

    @Test
    void fullTransformWithAllFields() {
        Timestamp ts = ts(1770711386, 748000000);
        UserTaskRun utr = UserTaskRun.newBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-1"))
                        .setUserTaskGuid("guid-1"))
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName("approve-order")
                        .setVersion(2))
                .setStatus(UserTaskRunStatus.ASSIGNED)
                .setUserId("luke")
                .setUserGroup("jedi-council")
                .setScheduledTime(ts)
                .build();

        ObjectNode result = UserTaskRunTransformer.transform(utr);

        assertEquals("run-1/guid-1", result.get("id").asText());
        assertEquals("run-1", result.get("wfRunId").asText());
        assertEquals("approve-order", result.get("userTaskDefName").asText());
        assertEquals(2, result.get("userTaskDefVersion").asInt());
        assertEquals("ASSIGNED", result.get("status").asText());
        assertEquals("luke", result.get("userId").asText());
        assertEquals("jedi-council", result.get("userGroup").asText());
        assertTrue(result.has("results"));
        assertTrue(result.get("results").isEmpty());
        assertNotNull(result.get("createdAt"));
    }

    @Test
    void missingUserIdAndUserGroupProduceNulls() {
        Timestamp ts = ts(1770711386, 0);
        UserTaskRun utr = UserTaskRun.newBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-2"))
                        .setUserTaskGuid("guid-2"))
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName("review-doc")
                        .setVersion(0))
                .setStatus(UserTaskRunStatus.UNASSIGNED)
                .setScheduledTime(ts)
                .build();

        ObjectNode result = UserTaskRunTransformer.transform(utr);

        assertTrue(result.get("userId").isNull());
        assertTrue(result.get("userGroup").isNull());
    }

    @Test
    void resultsMapIsTransformed() {
        Timestamp ts = ts(1770711386, 0);
        UserTaskRun utr = UserTaskRun.newBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-3"))
                        .setUserTaskGuid("guid-3"))
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName("fill-form")
                        .setVersion(1))
                .setStatus(UserTaskRunStatus.DONE)
                .setScheduledTime(ts)
                .putResults("approved", VariableValue.newBuilder().setBool(true).build())
                .putResults("comment", VariableValue.newBuilder().setStr("looks good").build())
                .build();

        ObjectNode result = UserTaskRunTransformer.transform(utr);

        assertTrue(result.get("results").get("approved").asBoolean());
        assertEquals("looks good", result.get("results").get("comment").asText());
    }

    @Test
    void emptyResultsMapProducesEmptyJsonObject() {
        Timestamp ts = ts(1770711386, 0);
        UserTaskRun utr = UserTaskRun.newBuilder()
                .setId(UserTaskRunId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-4"))
                        .setUserTaskGuid("guid-4"))
                .setUserTaskDefId(UserTaskDefId.newBuilder()
                        .setName("simple-task")
                        .setVersion(0))
                .setStatus(UserTaskRunStatus.ASSIGNED)
                .setScheduledTime(ts)
                .build();

        ObjectNode result = UserTaskRunTransformer.transform(utr);

        assertTrue(result.has("results"));
        assertTrue(result.get("results").isObject());
        assertTrue(result.get("results").isEmpty());
    }

    private static Timestamp ts(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }
}
