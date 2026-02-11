package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.google.protobuf.InvalidProtocolBufferException;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.streamsense.diffuser.topology.transformer.ExternalEventTransformer;
import io.littlehorse.streamsense.diffuser.topology.transformer.UserTaskRunTransformer;
import io.littlehorse.streamsense.diffuser.topology.transformer.WfRunTransformer;
import io.littlehorse.streamsense.diffuser.topology.transformer.WorkflowEventTransformer;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core diffuser processor (sub-topology 1). Consumes execution records,
 * routes by record type, transforms to JSON, and forwards with streamlet-topic header.
 */
public class DiffuserProcessor implements Processor<String, OutputTopicRecord, String, JsonNode> {

    private static final Logger log = LoggerFactory.getLogger(DiffuserProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String WFRUN_STORE = "wfrun-store";
    public static final String GLOBAL_METADATA_STORE = "global-metadata-store";

    private final TenantResolver tenantResolver;
    private final RecordRouter recordRouter;

    private ProcessorContext<String, JsonNode> context;
    private KeyValueStore<String, byte[]> wfRunStore;
    private ReadOnlyKeyValueStore<String, ValueAndTimestamp<byte[]>> globalMetadataStore;
    private final Map<String, Set<String>> publicVarCache = new HashMap<>();

    public DiffuserProcessor(TenantResolver tenantResolver, RecordRouter recordRouter) {
        this.tenantResolver = tenantResolver;
        this.recordRouter = recordRouter;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(ProcessorContext<String, JsonNode> context) {
        this.context = context;
        this.wfRunStore = context.getStateStore(WFRUN_STORE);
        this.globalMetadataStore = context.getStateStore(GLOBAL_METADATA_STORE);
    }

    @Override
    public void process(Record<String, OutputTopicRecord> record) {
        if (record.value() == null) return;

        try {
            String topic = context.recordMetadata().get().topic();
            String tenant = tenantResolver.resolve(topic);
            OutputTopicRecord otr = record.value();

            switch (otr.getPayloadCase()) {
                case WF_RUN -> processWfRun(record.key(), tenant, otr, record.timestamp());
                case VARIABLE -> processVariable(record.key(), tenant, otr, record.timestamp());
                case USER_TASK_RUN -> processUserTaskRun(record.key(), tenant, otr, record.timestamp());
                case EXTERNAL_EVENT -> processExternalEvent(record.key(), tenant, otr, record.timestamp());
                case WORKFLOW_EVENT -> processWorkflowEvent(record.key(), tenant, otr, record.timestamp());
                case TASK_RUN, CORRELATED_EVENT, TASK_CHECKPOINT, PAYLOAD_NOT_SET -> {
                    // Silently skip — these record types are not used by any Streamlet
                }
            }
        } catch (Exception e) {
            log.error("Error processing execution record: key={}", record.key(), e);
        }
    }

    private void processWfRun(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        WfRun wfRun = otr.getWfRun();
        String wfSpecName = wfRun.getWfSpecId().getName();

        Optional<String> targetTopic = recordRouter.resolveWfSpecTopic(tenant, wfSpecName);
        if (targetTopic.isEmpty()) return;

        String storeKey = tenant + "/" + wfRunId;
        ObjectNode state = loadWfRunState(storeKey);
        state = WfRunTransformer.applyWfRun(wfRun, state, otr.getTimestamp());
        saveWfRunState(storeKey, state);

        forward(wfRunId, state, targetTopic.get(), timestamp);
    }

    private void processVariable(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        Variable variable = otr.getVariable();
        String wfSpecName = variable.getWfSpecId().getName();

        Optional<String> targetTopic = recordRouter.resolveWfSpecTopic(tenant, wfSpecName);
        if (targetTopic.isEmpty()) return;

        Set<String> publicVars = resolvePublicVariables(tenant, variable.getWfSpecId());
        if (publicVars != null && !publicVars.contains(variable.getId().getName())) {
            return;
        }

        String storeKey = tenant + "/" + wfRunId;
        ObjectNode state = loadWfRunState(storeKey);
        state = WfRunTransformer.applyVariable(variable, state, wfRunId, otr.getTimestamp());
        saveWfRunState(storeKey, state);

        forward(wfRunId, state, targetTopic.get(), timestamp);
    }

    private void processUserTaskRun(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        UserTaskRun userTaskRun = otr.getUserTaskRun();
        String defName = UserTaskRunTransformer.extractDefName(userTaskRun);

        Optional<String> targetTopic = recordRouter.resolveUserTaskDefTopic(tenant, defName);
        if (targetTopic.isEmpty()) return;

        ObjectNode output = UserTaskRunTransformer.transform(userTaskRun);
        String outputKey = wfRunId + "/" + userTaskRun.getId().getUserTaskGuid();
        forward(outputKey, output, targetTopic.get(), timestamp);
    }

    private void processExternalEvent(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        ExternalEvent event = otr.getExternalEvent();
        String defName = ExternalEventTransformer.extractDefName(event);

        Optional<String> targetTopic = recordRouter.resolveExternalEventDefTopic(tenant, defName);
        if (targetTopic.isEmpty()) return;

        ObjectNode output = ExternalEventTransformer.transform(event);
        String outputKey = output.get("id").asText();
        forward(outputKey, output, targetTopic.get(), timestamp);
    }

    private void processWorkflowEvent(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        WorkflowEvent event = otr.getWorkflowEvent();
        String defName = WorkflowEventTransformer.extractDefName(event);

        Optional<String> targetTopic = recordRouter.resolveWorkflowEventDefTopic(tenant, defName);
        if (targetTopic.isEmpty()) return;

        ObjectNode output = WorkflowEventTransformer.transform(event);
        String outputKey = output.get("id").asText();
        forward(outputKey, output, targetTopic.get(), timestamp);
    }

    private Set<String> resolvePublicVariables(String tenant, WfSpecId wfSpecId) {
        String metaKey = WfSpecAccessFilter.metadataStoreKey(
                tenant, wfSpecId.getName(), wfSpecId.getMajorVersion(), wfSpecId.getRevision());

        return publicVarCache.computeIfAbsent(metaKey, key -> {
            ValueAndTimestamp<byte[]> entry = globalMetadataStore.get(key);
            if (entry == null) {
                log.warn("WfSpec metadata not found in global store: key={}", key);
                return null;
            }
            try {
                MetadataOutputTopicRecord record = MetadataOutputTopicRecord.parseFrom(entry.value());
                return WfSpecAccessFilter.publicVariableNames(record.getWfSpec());
            } catch (InvalidProtocolBufferException e) {
                log.error("Failed to parse WfSpec metadata: key={}", key, e);
                return null;
            }
        });
    }

    private void forward(String key, JsonNode value, String targetTopic, long timestamp) {
        Record<String, JsonNode> outRecord = new Record<>(key, value, timestamp);
        outRecord.headers().add(new RecordHeader(
                StreamletTopicExtractor.STREAMLET_TOPIC_HEADER,
                targetTopic.getBytes(StandardCharsets.UTF_8)));
        context.forward(outRecord);
    }

    private ObjectNode loadWfRunState(String storeKey) {
        byte[] bytes = wfRunStore.get(storeKey);
        if (bytes == null) return null;
        try {
            return (ObjectNode) MAPPER.readTree(bytes);
        } catch (Exception e) {
            log.warn("Failed to deserialize wfrun-store entry: key={}", storeKey, e);
            return null;
        }
    }

    private void saveWfRunState(String storeKey, ObjectNode state) {
        try {
            wfRunStore.put(storeKey, MAPPER.writeValueAsBytes(state));
        } catch (Exception e) {
            log.error("Failed to serialize wfrun-store entry: key={}", storeKey, e);
        }
    }
}
