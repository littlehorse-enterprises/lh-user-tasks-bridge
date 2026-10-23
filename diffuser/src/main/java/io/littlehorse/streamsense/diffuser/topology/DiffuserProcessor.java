package io.littlehorse.streamsense.diffuser.topology;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.littlehorse.sdk.common.proto.*;
import io.littlehorse.streamsense.diffuser.config.AppConfig;
import io.littlehorse.streamsense.diffuser.topology.transformer.ExternalEventTransformer;
import io.littlehorse.streamsense.diffuser.topology.transformer.UserTaskRunTransformer;
import io.littlehorse.streamsense.diffuser.topology.transformer.WfRunTransformer;
import io.littlehorse.streamsense.diffuser.topology.transformer.WorkflowEventTransformer;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.streams.KeyValue;
import org.apache.kafka.streams.processor.PunctuationType;
import org.apache.kafka.streams.processor.api.Processor;
import org.apache.kafka.streams.processor.api.ProcessorContext;
import org.apache.kafka.streams.processor.api.Record;
import org.apache.kafka.streams.state.KeyValueIterator;
import org.apache.kafka.streams.state.KeyValueStore;
import org.apache.kafka.streams.state.ReadOnlyKeyValueStore;
import org.apache.kafka.streams.state.ValueAndTimestamp;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Core execution-path processor. Consumes {@link OutputTopicRecord} from execution topics,
 * dispatches by payload type, transforms to JSON, and forwards with a {@code streamlet-topic}
 * header so the downstream sink routes each record to the correct streamlet topic.
 *
 * <p>Processing modes:
 * <ul>
 *   <li><b>WfRun / Variable</b> — stateful aggregation via {@code wfrun-store}.
 *       Each WfRun update and variable change is merged into a running JSON state keyed
 *       by {@code tenant/wfRunId}. Private variables are filtered using WfSpec metadata
 *       from the {@code global-metadata-store}.</li>
 *   <li><b>UserTaskRun / ExternalEvent / WorkflowEvent</b> — stateless transform-and-forward.</li>
 *   <li><b>TaskRun / CorrelatedEvent / TaskCheckpoint</b> — silently dropped (no streamlet).</li>
 * </ul>
 *
 * <p>Terminal WfRun entries are evicted from the {@code wfrun-store} after a configurable
 * retention period. See {@code docs/design/details/wfrun-store-eviction.md} for the full design.
 *
 * <p>Records for unconfigured specs (not in {@link RecordRouter}) are skipped.
 */
public class DiffuserProcessor implements Processor<String, OutputTopicRecord, String, JsonNode> {

    private static final Logger log = LoggerFactory.getLogger(DiffuserProcessor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static final String WFRUN_STORE = "wfrun-store";
    public static final String WFRUN_EVICTION_STORE = "wfrun-eviction-store";
    public static final String GLOBAL_METADATA_STORE = "global-metadata-store";

    private final TenantResolver tenantResolver;
    private final RecordRouter recordRouter;
    private final AppConfig.EvictionConfig evictionConfig;

    private ProcessorContext<String, JsonNode> context;
    private KeyValueStore<String, byte[]> wfRunStore;
    private KeyValueStore<String, byte[]> evictionStore;
    private ReadOnlyKeyValueStore<String, ValueAndTimestamp<MetadataOutputTopicRecord>> globalMetadataStore;
    private final Map<String, Set<String>> publicVarCache = new HashMap<>();

    public DiffuserProcessor(TenantResolver tenantResolver, RecordRouter recordRouter,
                             AppConfig.EvictionConfig evictionConfig) {
        this.tenantResolver = tenantResolver;
        this.recordRouter = recordRouter;
        this.evictionConfig = evictionConfig;
    }

    @Override
    @SuppressWarnings("unchecked")
    public void init(ProcessorContext<String, JsonNode> context) {
        this.context = context;
        this.wfRunStore = context.getStateStore(WFRUN_STORE);
        this.evictionStore = context.getStateStore(WFRUN_EVICTION_STORE);
        this.globalMetadataStore = context.getStateStore(GLOBAL_METADATA_STORE);

        context.schedule(
                Duration.ofSeconds(evictionConfig.sweepIntervalSeconds()),
                PunctuationType.WALL_CLOCK_TIME,
                this::evictExpiredEntries);
    }

    @Override
    public void process(Record<String, OutputTopicRecord> record) {
        if (record.value() == null) return;

        try {
            String topic = context.recordMetadata().get().topic();
            String tenant = tenantResolver.resolve(topic);
            OutputTopicRecord otr = record.value();

            log.debug("Processing record: key={}, tenant={}, payloadType={}", record.key(), tenant, otr.getPayloadCase());

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
        log.debug("WfRun update: storeKey={}, targetTopic={}", storeKey, targetTopic.get());
        ObjectNode state = loadWfRunState(storeKey);
        state = WfRunTransformer.applyWfRun(wfRun, state, otr.getTimestamp());

        if (isEvictionEligible(wfRun.getStatus())) {
            scheduleEviction(storeKey, state, wfRun.getWfSpecId());
        } else {
            cancelEviction(storeKey, state);
        }

        saveWfRunState(storeKey, state);
        forward(wfRunId, state, targetTopic.get(), timestamp);
    }

    private void processVariable(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        Variable variable = otr.getVariable();
        String wfSpecName = variable.getWfSpecId().getName();

        Optional<String> targetTopic = recordRouter.resolveWfSpecTopic(tenant, wfSpecName);
        if (targetTopic.isEmpty()) return;

        Set<String> publicVars = resolvePublicVariables(tenant, variable.getWfSpecId());
        // Metadata propagates asynchronously. Drop unknown events rather than storing
        // potentially private values; a later event can be accepted once metadata arrives.
        if (publicVars == null) {
            return;
        }
        if (!publicVars.contains(variable.getId().getName())) {
            log.debug("Variable filtered (not public): name={}, wfSpec={}", variable.getId().getName(), wfSpecName);
            return;
        }
        log.debug("Variable accepted: name={}, wfSpec={}", variable.getId().getName(), wfSpecName);

        String storeKey = tenant + "/" + wfRunId;
        ObjectNode state = loadWfRunState(storeKey);
        if (state == null) {
            log.warn("Variable update for evicted/unknown WfRun, skipping: storeKey={}, variable={}",
                    storeKey, variable.getId().getName());
            return;
        }
        state = WfRunTransformer.applyVariable(variable, state, wfRunId, otr.getTimestamp());
        saveWfRunState(storeKey, state);

        forward(wfRunId, state, targetTopic.get(), timestamp);
    }

    private void processUserTaskRun(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        UserTaskRun userTaskRun = otr.getUserTaskRun();
        String defName = UserTaskRunTransformer.extractDefName(userTaskRun);

        Optional<String> targetTopic = recordRouter.resolveUserTaskDefTopic(tenant, defName);
        if (targetTopic.isEmpty()) return;

        log.debug("UserTaskRun: def={}, targetTopic={}", defName, targetTopic.get());
        ObjectNode output = UserTaskRunTransformer.transform(userTaskRun);
        String outputKey = wfRunId + "/" + userTaskRun.getId().getUserTaskGuid();
        forward(outputKey, output, targetTopic.get(), timestamp);
    }

    private void processExternalEvent(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        ExternalEvent event = otr.getExternalEvent();
        String defName = ExternalEventTransformer.extractDefName(event);

        Optional<String> targetTopic = recordRouter.resolveExternalEventDefTopic(tenant, defName);
        if (targetTopic.isEmpty()) return;

        log.debug("ExternalEvent: def={}, targetTopic={}", defName, targetTopic.get());
        ObjectNode output = ExternalEventTransformer.transform(event);
        String outputKey = output.get("id").asText();
        forward(outputKey, output, targetTopic.get(), timestamp);
    }

    private void processWorkflowEvent(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
        WorkflowEvent event = otr.getWorkflowEvent();
        String defName = WorkflowEventTransformer.extractDefName(event);

        Optional<String> targetTopic = recordRouter.resolveWorkflowEventDefTopic(tenant, defName);
        if (targetTopic.isEmpty()) return;

        log.debug("WorkflowEvent: def={}, targetTopic={}", defName, targetTopic.get());
        ObjectNode output = WorkflowEventTransformer.transform(event);
        String outputKey = output.get("id").asText();
        forward(outputKey, output, targetTopic.get(), timestamp);
    }

    // --- Eviction logic ---

    static boolean isEvictionEligible(LHStatus status) {
        return status == LHStatus.COMPLETED
                || status == LHStatus.ERROR
                || status == LHStatus.EXCEPTION
                || status == LHStatus.UNRECOGNIZED;
    }

    private void scheduleEviction(String storeKey, ObjectNode state, WfSpecId wfSpecId) {
        cancelEviction(storeKey, state);

        long retentionSeconds = resolveRetentionSeconds(storeKey, wfSpecId);
        long updatedAtMillis = Instant.parse(state.get("updatedAt").asText()).toEpochMilli();
        long evictAtMillis = updatedAtMillis + (retentionSeconds * 1000);
        String evictionKey = String.format("%016d/%s", evictAtMillis, storeKey);

        evictionStore.put(evictionKey, new byte[0]);
        state.put("_evictionKey", evictionKey);
    }

    private void cancelEviction(String storeKey, ObjectNode state) {
        if (state != null && state.has("_evictionKey")) {
            String evictionKey = state.get("_evictionKey").asText();
            evictionStore.delete(evictionKey);
            state.remove("_evictionKey");
        }
    }

    private long resolveRetentionSeconds(String storeKey, WfSpecId wfSpecId) {
        String metaKey = WfSpecAccessFilter.metadataStoreKey(
                storeKey.substring(0, storeKey.indexOf('/')),
                wfSpecId.getName(), wfSpecId.getMajorVersion(), wfSpecId.getRevision());

        ValueAndTimestamp<MetadataOutputTopicRecord> entry = globalMetadataStore.get(metaKey);
        if (entry != null) {
            WfSpec wfSpec = entry.value().getWfSpec();
            if (wfSpec.hasRetentionPolicy()
                    && wfSpec.getRetentionPolicy().getWfGcPolicyCase()
                    == WorkflowRetentionPolicy.WfGcPolicyCase.SECONDS_AFTER_WF_TERMINATION) {
                return wfSpec.getRetentionPolicy().getSecondsAfterWfTermination();
            }
        }
        return evictionConfig.fallbackRetentionSeconds();
    }

    private void evictExpiredEntries(long timestamp) {
        log.debug("Eviction sweep START");
        String upperBound = String.format("%016d0", timestamp);
        int count = 0;

        try (KeyValueIterator<String, byte[]> iterator = evictionStore.range(null, upperBound)) {
            while (iterator.hasNext()) {
                KeyValue<String, byte[]> entry = iterator.next();
                String wfrunStoreKey = entry.key.substring(17); // skip "{016d}/"

                wfRunStore.delete(wfrunStoreKey);
                evictionStore.delete(entry.key);
                count++;

                log.trace("Evicted wfrun-store entry: key={}, evictionKey={}", wfrunStoreKey, entry.key);
            }
        }

        if (count > 0) {
            log.info("Eviction sweep completed: evicted={}", count);
        }
        log.debug("Eviction sweep END");
    }

    /**
     * Returns a deep copy of the state with internal bookkeeping fields removed.
     * The {@code _evictionKey} field is used internally for eviction scheduling
     * and must not be forwarded to downstream consumers.
     */
    static ObjectNode stripInternalFields(ObjectNode state) {
        ObjectNode output = state.deepCopy();
        output.remove("_evictionKey");
        return output;
    }

    // --- Shared helpers ---

    /**
     * Looks up the set of PUBLIC_VAR variable names for a WfSpec from the global metadata store.
     * Results are cached per WfSpec version to avoid repeated store lookups.
     *
     * @return the set of public variable names, or {@code null} if the WfSpec is not in the store
     */
    private Set<String> resolvePublicVariables(String tenant, WfSpecId wfSpecId) {
        String metaKey = WfSpecAccessFilter.metadataStoreKey(
                tenant, wfSpecId.getName(), wfSpecId.getMajorVersion(), wfSpecId.getRevision());

        return publicVarCache.computeIfAbsent(metaKey, key -> {
            ValueAndTimestamp<MetadataOutputTopicRecord> entry = globalMetadataStore.get(key);
            if (entry == null) {
                log.warn("WfSpec metadata not found in global store: key={}", key);
                return null;
            }
            return WfSpecAccessFilter.publicVariableNames(entry.value().getWfSpec());
        });
    }

    /**
     * Strips internal fields, adds the {@code streamlet-topic} header, and forwards
     * the JSON record downstream. Internal fields (e.g. {@code _evictionKey}) are removed
     * from {@link ObjectNode} values to prevent leakage to downstream consumers.
     */
    private void forward(String key, JsonNode value, String targetTopic, long timestamp) {
        log.debug("Forwarding: key={}, targetTopic={}", key, targetTopic);
        JsonNode outputValue = (value instanceof ObjectNode on) ? stripInternalFields(on) : value;
        Record<String, JsonNode> outRecord = new Record<>(key, outputValue, timestamp);
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
