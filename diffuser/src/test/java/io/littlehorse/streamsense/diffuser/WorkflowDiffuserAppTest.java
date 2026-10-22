package io.littlehorse.streamsense.diffuser;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class WorkflowDiffuserAppTest {

    @Test
    void mainClassExists() {
        assertDoesNotThrow(() -> Class.forName("io.littlehorse.streamsense.diffuser.WorkflowDiffuserApp"));
    }
}
