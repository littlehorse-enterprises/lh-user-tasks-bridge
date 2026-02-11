package io.littlehorse.examples;

import io.littlehorse.sdk.common.config.LHConfig;
import io.littlehorse.sdk.common.proto.Comparator;
import io.littlehorse.sdk.common.proto.LittleHorseGrpc.LittleHorseBlockingStub;
import io.littlehorse.sdk.common.proto.OutputTopicConfig;
import io.littlehorse.sdk.common.proto.PutExternalEventDefRequest;
import io.littlehorse.sdk.common.proto.PutTenantRequest;
import io.littlehorse.sdk.common.proto.PutWorkflowEventDefRequest;
import io.littlehorse.sdk.common.proto.VariableType;
import io.littlehorse.sdk.usertask.UserTaskSchema;
import io.littlehorse.sdk.wfsdk.NodeOutput;
import io.littlehorse.sdk.wfsdk.UserTaskOutput;
import io.littlehorse.sdk.wfsdk.WfRunVariable;
import io.littlehorse.sdk.wfsdk.Workflow;
import io.littlehorse.sdk.wfsdk.WorkflowThread;
import io.littlehorse.sdk.wfsdk.internal.WorkflowImpl;
import io.littlehorse.sdk.worker.LHTaskWorker;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleRunner {

    private static final Logger log = LoggerFactory.getLogger(ExampleRunner.class);

    public static void main(String[] args) throws IOException, InterruptedException {
        Properties props = getConfigProps();

        // Default tenant config
        LHConfig defaultConfig = new LHConfig(props);
        LittleHorseBlockingStub defaultClient = defaultConfig.getBlockingStub();

        // Cars tenant config
        Properties carsProps = new Properties();
        carsProps.putAll(props);
        carsProps.put("LHC_TENANT_ID", "cars");
        LHConfig carsConfig = new LHConfig(carsProps);
        LittleHorseBlockingStub carsClient = carsConfig.getBlockingStub();

        // 1. Register tenants with output topic config
        log.info("Registering tenants...");
        defaultClient.putTenant(PutTenantRequest.newBuilder()
                .setId("default")
                .setOutputTopicConfig(OutputTopicConfig.newBuilder())
                .build());
        defaultClient.putTenant(PutTenantRequest.newBuilder()
                .setId("cars")
                .setOutputTopicConfig(OutputTopicConfig.newBuilder())
                .build());

        // Allow output topic creation
        log.info("Waiting for output topics to be created...");
        Thread.sleep(5000);

        // 2. Create all task workers
        List<LHTaskWorker> allWorkers = new ArrayList<>();

        // Default tenant workers
        GreetWorker greetWorker = new GreetWorker();
        allWorkers.add(new LHTaskWorker(greetWorker, "greet", defaultConfig));
        allWorkers.add(new LHTaskWorker(greetWorker, "ask-for-name", defaultConfig));

        EmailWorker emailWorker = new EmailWorker();
        allWorkers.add(new LHTaskWorker(emailWorker, "send-email", defaultConfig));

        VariablesWorker variablesWorker = new VariablesWorker();
        allWorkers.add(new LHTaskWorker(variablesWorker, "sentiment-analysis", defaultConfig));
        allWorkers.add(new LHTaskWorker(variablesWorker, "process-text", defaultConfig));
        allWorkers.add(new LHTaskWorker(variablesWorker, "send", defaultConfig));

        // Cars tenant workers
        CarWorker carWorker = new CarWorker();
        allWorkers.add(new LHTaskWorker(carWorker, "greet", carsConfig));
        allWorkers.add(new LHTaskWorker(carWorker, "describe-car", carsConfig));

        // 3. Register all TaskDefs
        log.info("Registering TaskDefs...");
        for (LHTaskWorker worker : allWorkers) {
            log.info("  Registering TaskDef: {}", worker.getTaskDefName());
            worker.registerTaskDef();
        }

        // 4. Register ExternalEventDefs (default tenant)
        log.info("Registering ExternalEventDefs...");
        defaultClient.putExternalEventDef(
                PutExternalEventDefRequest.newBuilder().setName("name-event").build());

        // 5. Register WorkflowEventDefs (default tenant)
        log.info("Registering WorkflowEventDefs...");
        defaultClient.putWorkflowEventDef(
                PutWorkflowEventDefRequest.newBuilder().setName("sleep-done").build());

        // 6. Register UserTaskDefs (default tenant)
        log.info("Registering UserTaskDefs...");
        UserTaskSchema requestForm = new UserTaskSchema(new ItemRequestForm(), "it-request");
        defaultClient.putUserTaskDef(requestForm.compile());

        UserTaskSchema approvalForm = new UserTaskSchema(new ApprovalForm(), "approve-it-request");
        defaultClient.putUserTaskDef(approvalForm.compile());

        // 7. Register WfSpecs
        log.info("Registering WfSpecs...");

        log.info("  Registering output-topic (default tenant)");
        outputTopicWorkflow().registerWfSpec(defaultClient);

        log.info("  Registering example-external-event (default tenant)");
        externalEventWorkflow().registerWfSpec(defaultClient);

        log.info("  Registering await-wf-event (default tenant)");
        awaitWfEventWorkflow().registerWfSpec(defaultClient);

        log.info("  Registering example-variables (default tenant)");
        variablesWorkflow().registerWfSpec(defaultClient);

        log.info("  Registering it-request (default tenant)");
        itRequestWorkflow().registerWfSpec(defaultClient);

        log.info("  Registering example-json (cars tenant)");
        jsonWorkflow().registerWfSpec(carsClient);

        // 8. Start all task workers
        log.info("Starting task workers...");
        for (LHTaskWorker worker : allWorkers) {
            log.info("  Starting worker: {}", worker.getTaskDefName());
            worker.start();
        }

        // 9. Shutdown hooks
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            log.info("Shutting down workers...");
            for (LHTaskWorker worker : allWorkers) {
                worker.close();
            }
        }));

        log.info("All examples registered and workers started. Press Ctrl+C to stop.");
    }

    // -- Workflow definitions --

    static Workflow outputTopicWorkflow() {
        return new WorkflowImpl("output-topic", wf -> {
            WfRunVariable theName = wf.declareStr("input-name").searchable().asPublic();
            WfRunVariable ignoredGreeting = wf.declareStr("ignored");
            WfRunVariable publicGreeting = wf.declareStr("public-greeting").asPublic();

            NodeOutput result = wf.execute("greet", theName);

            ignoredGreeting.assign(result);
            publicGreeting.assign(result);
        });
    }

    static Workflow externalEventWorkflow() {
        return new WorkflowImpl("example-external-event", wf -> {
            WfRunVariable name = wf.declareStr("name").searchable();

            wf.execute("ask-for-name");

            name.assign(wf.waitForEvent("name-event"));

            wf.execute("greet", name);
        });
    }

    static Workflow awaitWfEventWorkflow() {
        return new WorkflowImpl("await-wf-event", wf -> {
            WfRunVariable sleepTime = wf.declareInt("sleep-time").required();
            wf.sleepSeconds(sleepTime);
            wf.throwEvent("sleep-done", "hello there!");
        });
    }

    static Workflow variablesWorkflow() {
        return new WorkflowImpl("example-variables", wf -> {
            WfRunVariable inputText = wf.declareStr("input-text").searchable().masked();
            WfRunVariable addLength = wf.declareBool("add-length").searchable();
            WfRunVariable userId = wf.declareInt("user-id").searchable();
            WfRunVariable sentimentScore = wf.declareDouble("sentiment-score").searchable();
            WfRunVariable processedResult = wf.declareJsonObj("processed-result")
                    .searchableOn("$.sentimentScore", VariableType.DOUBLE)
                    .masked();

            NodeOutput sentimentAnalysisOutput = wf.execute("sentiment-analysis", inputText);
            sentimentScore.assign(sentimentAnalysisOutput);
            NodeOutput processedTextOutput = wf.execute("process-text", inputText, sentimentScore, addLength, userId);
            processedResult.assign(processedTextOutput);
            wf.execute("send", processedResult);
        });
    }

    static Workflow itRequestWorkflow() {
        return new WorkflowImpl("it-request", ExampleRunner::itRequestWf);
    }

    private static void itRequestWf(WorkflowThread wf) {
        WfRunVariable userId = wf.declareStr("user-id");
        WfRunVariable itRequest = wf.declareJsonObj("it-request");
        WfRunVariable isApproved = wf.declareBool("is-approved");

        // Get the IT Request
        UserTaskOutput formOutput = wf.assignUserTask("it-request", userId, "testGroup");
        wf.releaseToGroupOnDeadline(formOutput, 60);

        wf.handleException(formOutput, handler -> {
            String email = "test-ut-support@gmail.com";
            handler.execute("send-email", email, "Task cancelled");
        });
        itRequest.assign(formOutput);

        // Have Finance approve the request
        UserTaskOutput financeUserTaskOutput = wf.assignUserTask("approve-it-request", null, "finance")
                .withNotes(wf.format(
                        "User {0} is requesting to buy item {1}.\nJustification: {2}",
                        userId, itRequest.jsonPath("$.requestedItem"), itRequest.jsonPath("$.justification")));
        String financeTeamEmailBody = "Hi finance team, you have a new assigned task";
        String financeTeamEmail = "finance@gmail.com";
        wf.scheduleReminderTask(financeUserTaskOutput, 2, "send-email", financeTeamEmail, financeTeamEmailBody);
        wf.reassignUserTask(financeUserTaskOutput, "test-eduwer", null, 60);

        isApproved.assign(financeUserTaskOutput.jsonPath("$.isApproved"));

        wf.doIf(
                        wf.condition(isApproved, Comparator.EQUALS, true),
                        ifBody -> {
                            ifBody.execute(
                                    "send-email",
                                    userId,
                                    wf.format(
                                            "Dear {0}, your request for {1} has been approved!",
                                            userId, itRequest.jsonPath("$.requestedItem")));
                        })
                .doElse(
                        elseBody -> {
                            elseBody.execute(
                                    "send-email",
                                    userId,
                                    wf.format(
                                            "Dear {0}, your request for {1} has been denied.",
                                            userId, itRequest.jsonPath("$.requestedItem")));
                        });
    }

    static Workflow jsonWorkflow() {
        return new WorkflowImpl("example-json", wf -> {
            WfRunVariable person = wf.declareJsonObj("person");

            wf.execute("greet", person.jsonPath("$.name"));
            wf.execute("describe-car", person.jsonPath("$.car"));
        });
    }

    // -- Config loading --

    private static Properties getConfigProps() throws IOException {
        Properties props = new Properties();
        File configPath = Path.of(System.getProperty("user.home"), ".config/littlehorse.config")
                .toFile();
        if (configPath.exists()) {
            props.load(new FileInputStream(configPath));
        }
        return props;
    }
}
