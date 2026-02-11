package io.littlehorse.streamsense.diffuser.topology;

import io.littlehorse.streamsense.diffuser.config.AppConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.*;

/**
 * Determines if a record should be processed and what the target output topic is,
 * based on the configured specs for each tenant.
 */
public class RecordRouter {

    private static final Logger log = LoggerFactory.getLogger(RecordRouter.class);

    private final TopicNameResolver topicNameResolver;
    // tenant -> set of spec names
    private final Map<String, Set<String>> wfSpecsByTenant;
    private final Map<String, Set<String>> userTaskDefsByTenant;
    private final Map<String, Set<String>> externalEventDefsByTenant;
    private final Map<String, Set<String>> workflowEventDefsByTenant;

    public RecordRouter(AppConfig appConfig) {
        this.topicNameResolver = new TopicNameResolver(appConfig.lhClusterId());
        this.wfSpecsByTenant = new HashMap<>();
        this.userTaskDefsByTenant = new HashMap<>();
        this.externalEventDefsByTenant = new HashMap<>();
        this.workflowEventDefsByTenant = new HashMap<>();

        for (AppConfig.TenantConfig tenant : appConfig.tenants()) {
            String name = tenant.name();
            tenant.wfSpecs().ifPresent(specs -> wfSpecsByTenant.put(name, new HashSet<>(specs)));
            tenant.userTaskDefs().ifPresent(specs -> userTaskDefsByTenant.put(name, new HashSet<>(specs)));
            tenant.externalEventDefs().ifPresent(specs -> externalEventDefsByTenant.put(name, new HashSet<>(specs)));
            tenant.workflowEventDefs().ifPresent(specs -> workflowEventDefsByTenant.put(name, new HashSet<>(specs)));
        }
    }

    /**
     * Resolves the output topic for a WfSpec streamlet, or empty if the spec is not
     * configured for this tenant.
     */
    public Optional<String> resolveWfSpecTopic(String tenant, String wfSpecName) {
        Set<String> specs = wfSpecsByTenant.get(tenant);
        if (specs != null && specs.contains(wfSpecName)) {
            String topic = topicNameResolver.resolve(tenant, StreamletType.WORKFLOW, wfSpecName);
            log.debug("Resolved WfSpec topic: tenant={}, spec={}, topic={}", tenant, wfSpecName, topic);
            return Optional.of(topic);
        }
        log.debug("WfSpec not configured, skipping: tenant={}, spec={}", tenant, wfSpecName);
        return Optional.empty();
    }

    /** Resolves the output topic for a UserTaskDef streamlet, or empty if not configured. */
    public Optional<String> resolveUserTaskDefTopic(String tenant, String userTaskDefName) {
        Set<String> specs = userTaskDefsByTenant.get(tenant);
        if (specs != null && specs.contains(userTaskDefName)) {
            String topic = topicNameResolver.resolve(tenant, StreamletType.USERTASK, userTaskDefName);
            log.debug("Resolved UserTaskDef topic: tenant={}, spec={}, topic={}", tenant, userTaskDefName, topic);
            return Optional.of(topic);
        }
        log.debug("UserTaskDef not configured, skipping: tenant={}, spec={}", tenant, userTaskDefName);
        return Optional.empty();
    }

    /** Resolves the output topic for an ExternalEventDef streamlet, or empty if not configured. */
    public Optional<String> resolveExternalEventDefTopic(String tenant, String externalEventDefName) {
        Set<String> specs = externalEventDefsByTenant.get(tenant);
        if (specs != null && specs.contains(externalEventDefName)) {
            String topic = topicNameResolver.resolve(tenant, StreamletType.EXTERNALEVENT, externalEventDefName);
            log.debug("Resolved ExternalEventDef topic: tenant={}, spec={}, topic={}", tenant, externalEventDefName, topic);
            return Optional.of(topic);
        }
        log.debug("ExternalEventDef not configured, skipping: tenant={}, spec={}", tenant, externalEventDefName);
        return Optional.empty();
    }

    /** Resolves the output topic for a WorkflowEventDef streamlet, or empty if not configured. */
    public Optional<String> resolveWorkflowEventDefTopic(String tenant, String workflowEventDefName) {
        Set<String> specs = workflowEventDefsByTenant.get(tenant);
        if (specs != null && specs.contains(workflowEventDefName)) {
            String topic = topicNameResolver.resolve(tenant, StreamletType.WORKFLOWEVENT, workflowEventDefName);
            log.debug("Resolved WorkflowEventDef topic: tenant={}, spec={}, topic={}", tenant, workflowEventDefName, topic);
            return Optional.of(topic);
        }
        log.debug("WorkflowEventDef not configured, skipping: tenant={}, spec={}", tenant, workflowEventDefName);
        return Optional.empty();
    }
}
