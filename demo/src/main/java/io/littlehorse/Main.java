package io.littlehorse;

import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc;
import io.littlehorse.sdk.usertask.UserTaskSchema;
import io.littlehorse.sdk.usertask.annotations.UserTaskField;
import io.littlehorse.sdk.wfsdk.Workflow;
import io.littlehorse.sdk.wfsdk.internal.WorkflowImpl;

public class Main {

    private static final String USER_TASKS_FORM = "user-tasks-form";
    private static final UserTaskSchema userTaskSchema = new UserTaskSchema(
            new UserTasksForm(),
            USER_TASKS_FORM
    );
    private static final Workflow workflow = new WorkflowImpl("user-tasks", wf -> {
        wf.assignUserTask(
                USER_TASKS_FORM,
                null,
                "admins"
        );
    });
    private static final LHConfig config = new LHConfig();
    private static final LittleHorseGrpc.LittleHorseBlockingStub client = config.getBlockingStub();

    public static void main(String[] args) {
        client.putUserTaskDef(userTaskSchema.compile());
        workflow.registerWfSpec(client);
    }

    public static class UserTasksForm {

        @UserTaskField(
                displayName = "How many?",
                description = "An integer value."
        )
        public int quantity;

        @UserTaskField(
                displayName = "How much?",
                description = "A double value."
        )
        public double totalCost;


        @UserTaskField(
                displayName = "Describe it",
                description = "A string value."
        )
        public String description;

        @UserTaskField(
                displayName = "Approved?",
                description = "Reply 'true' if this is an acceptable request."
        )
        public boolean isApproved;
    }
}
