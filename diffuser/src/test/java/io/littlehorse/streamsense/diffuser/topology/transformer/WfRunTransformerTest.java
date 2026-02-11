package io.littlehorse.streamsense.diffuser.topology.transformer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.Timestamp;
import io.littlehorse.sdk.common.proto.*;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WfRunTransformerTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void applyWfRunWithNullStateCreatesNewState() {
        Timestamp ts = ts(1770710673, 531000000);
        WfRun wfRun = WfRun.newBuilder()
                .setId(WfRunId.newBuilder().setId("run-1"))
                .setStatus(LHStatus.RUNNING)
                .setStartTime(ts)
                .build();

        ObjectNode result = WfRunTransformer.applyWfRun(wfRun, null, ts);

        assertEquals("run-1", result.get("id").asText());
        assertEquals("RUNNING", result.get("status").asText());
        assertTrue(result.has("columns"));
        assertTrue(result.get("columns").isEmpty());
        assertNotNull(result.get("createdAt"));
        assertNotNull(result.get("updatedAt"));
    }

    @Test
    void applyWfRunWithExistingStatePreservesColumns() {
        Timestamp ts1 = ts(1770710673, 0);
        Timestamp ts2 = ts(1770710733, 0);

        // Build initial state with a column
        ObjectNode existing = MAPPER.createObjectNode();
        existing.put("id", "run-1");
        existing.put("status", "RUNNING");
        ObjectNode columns = MAPPER.createObjectNode();
        columns.put("user-id", "obi-wan");
        existing.set("columns", columns);
        existing.put("createdAt", "2026-02-11T00:00:00.000Z");

        WfRun wfRun = WfRun.newBuilder()
                .setId(WfRunId.newBuilder().setId("run-1"))
                .setStatus(LHStatus.ERROR)
                .setStartTime(ts1)
                .build();

        ObjectNode result = WfRunTransformer.applyWfRun(wfRun, existing, ts2);

        assertEquals("ERROR", result.get("status").asText());
        assertEquals("obi-wan", result.get("columns").get("user-id").asText());
    }

    @Test
    void applyVariableWithNullStateCreatesSkeleton() {
        Timestamp ts = ts(1770710673, 0);
        Variable variable = Variable.newBuilder()
                .setId(VariableId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-1"))
                        .setName("order-status")
                        .setThreadRunNumber(0))
                .setValue(VariableValue.newBuilder().setStr("SHIPPING"))
                .build();

        ObjectNode result = WfRunTransformer.applyVariable(variable, null, "run-1", ts);

        assertEquals("run-1", result.get("id").asText());
        assertTrue(result.has("columns"));
        assertEquals("SHIPPING", result.get("columns").get("order-status").asText());
        assertNotNull(result.get("updatedAt"));
    }

    @Test
    void applyVariableWithExistingStateAddsToColumns() {
        Timestamp ts = ts(1770710733, 0);

        ObjectNode existing = MAPPER.createObjectNode();
        existing.put("id", "run-1");
        existing.put("status", "RUNNING");
        ObjectNode columns = MAPPER.createObjectNode();
        columns.put("user-id", "obi-wan");
        existing.set("columns", columns);

        Variable variable = Variable.newBuilder()
                .setId(VariableId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-1"))
                        .setName("order-status")
                        .setThreadRunNumber(0))
                .setValue(VariableValue.newBuilder().setStr("SHIPPING"))
                .build();

        ObjectNode result = WfRunTransformer.applyVariable(variable, existing, "run-1", ts);

        assertEquals("obi-wan", result.get("columns").get("user-id").asText());
        assertEquals("SHIPPING", result.get("columns").get("order-status").asText());
    }

    @Test
    void applyVariableOverwritesSameVariable() {
        Timestamp ts1 = ts(1770710673, 0);
        Timestamp ts2 = ts(1770710733, 0);

        Variable v1 = Variable.newBuilder()
                .setId(VariableId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-1"))
                        .setName("counter")
                        .setThreadRunNumber(0))
                .setValue(VariableValue.newBuilder().setInt(1))
                .build();

        ObjectNode state = WfRunTransformer.applyVariable(v1, null, "run-1", ts1);
        assertEquals(1, state.get("columns").get("counter").asInt());

        Variable v2 = Variable.newBuilder()
                .setId(VariableId.newBuilder()
                        .setWfRunId(WfRunId.newBuilder().setId("run-1"))
                        .setName("counter")
                        .setThreadRunNumber(0))
                .setValue(VariableValue.newBuilder().setInt(42))
                .build();

        ObjectNode updated = WfRunTransformer.applyVariable(v2, state, "run-1", ts2);
        assertEquals(42, updated.get("columns").get("counter").asInt());
    }

    private static Timestamp ts(long seconds, int nanos) {
        return Timestamp.newBuilder().setSeconds(seconds).setNanos(nanos).build();
    }
}
