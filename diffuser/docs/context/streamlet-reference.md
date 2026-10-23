# Workflow Diffuser Streamlet Reference

This document describes the Kafka input/output contracts implemented by the
Workflow Diffuser.

## 1. Inputs

The application subscribes only to topics for tenants configured under
`app.tenants`.

| Topic pattern | Key | Value | Purpose |
| --- | --- | --- | --- |
| `{lh-cluster-id}_{tenant}_execution` | `String` (normally the WfRun ID) | Native Protobuf binary `OutputTopicRecord` | Execution updates. |
| `{lh-cluster-id}_{tenant}_metadata` | `String` | Native Protobuf binary `MetadataOutputTopicRecord` | Metadata used by the diffuser. |

The metadata topics are consumed from the earliest offset so that the
application can build its metadata store. Execution offset behavior is
controlled by the Kafka Streams configuration (`latest` in the default
configuration).

Execution payload handling:

| `OutputTopicRecord` payload | Handling |
| --- | --- |
| `wf_run` | Updates a workflow streamlet. |
| `variable` | Updates a workflow streamlet when the variable is public. |
| `user_task_run` | Emits a user-task streamlet record. |
| `external_event` | Emits an external-event streamlet record. |
| `workflow_event` | Emits a workflow-event streamlet record. |
| `task_run`, `correlated_event`, `task_checkpoint`, or no payload | Dropped. |

## 2. Supported LH entities

| Streamlet entity | LH source entity | Processing model |
| --- | --- | --- |
| Workflow (configured WfSpec) | `WfRun`, `Variable` | Stateful aggregate keyed by WfRun ID. Only WfSpec variables marked public are included in `columns`. |
| User task (configured UserTaskDef) | `UserTaskRun` | Stateless transformation of a full snapshot. |
| External event (configured ExternalEventDef) | `ExternalEvent` | Stateless transformation. |
| Workflow event (configured WorkflowEventDef) | `WorkflowEvent` | Stateless transformation. |

`TaskDef` metadata and `TaskRun` execution records do not create streamlets.

## 3. Output topics by entity

The diffuser resolves target topics dynamically using this convention:

```text
{lh-cluster-id}_{tenant}_streamlet_{entity-prefix}-{configured-name}
```

| Streamlet entity | Entity prefix | Configured name | Kafka message key | Example topic |
| --- | --- | --- | --- | --- |
| Workflow / WfSpec | `workflow` | WfSpec name | `{wfRunId}` | `cluster1_default_streamlet_workflow-output-topic` |
| User task / UserTaskDef | `usertask` | UserTaskDef name | `{wfRunId}/{userTaskGuid}` | `cluster1_default_streamlet_usertask-it-request` |
| External event / ExternalEventDef | `externalevent` | ExternalEventDef name | `{wfRunId}/{externalEventDefName}/{guid}` | `cluster1_default_streamlet_externalevent-name-event` |
| Workflow event / WorkflowEventDef | `workflowevent` | WorkflowEventDef name | `{wfRunId}/{workflowEventDefName}/{number}` | `cluster1_default_streamlet_workflowevent-sleep-done` |

Records whose WfSpec or definition name is not configured for the tenant are
skipped.

## 4. Topics required by the Kafka Streams topology

The topology uses the following topics. Topic names marked as patterns are
expanded for every configured tenant or streamlet.

| Category | Topic name or pattern | Required by | Provisioning / notes |
| --- | --- | --- | --- |
| LH execution input | `{lh-cluster-id}_{tenant}_execution` | Execution source | Created and populated by LH for every configured tenant. |
| LH metadata input | `{lh-cluster-id}_{tenant}_metadata` | Metadata source | Created and populated by LH for every configured tenant. This is consumed from the earliest offset. |
| Workflow output | `{lh-cluster-id}_{tenant}_streamlet_workflow-{wf-spec}` | Dynamic streamlet sink | One topic per configured WfSpec. |
| User-task output | `{lh-cluster-id}_{tenant}_streamlet_usertask-{user-task-def}` | Dynamic streamlet sink | One topic per configured UserTaskDef. |
| External-event output | `{lh-cluster-id}_{tenant}_streamlet_externalevent-{external-event-def}` | Dynamic streamlet sink | One topic per configured ExternalEventDef. |
| Workflow-event output | `{lh-cluster-id}_{tenant}_streamlet_workflowevent-{workflow-event-def}` | Dynamic streamlet sink | One topic per configured WorkflowEventDef. |
| Metadata-store changelog | `{application.id}-global-metadata-changelog` | Metadata local store and `GlobalKTable` source | Kafka Streams-managed internal topic. It must be available when the topology starts because the `GlobalKTable` reads it. |
| WfRun-store changelog | `{application.id}-wfrun-store-changelog` | WfRun state store | Kafka Streams-managed internal topic that persists workflow aggregate state. |

With the default application ID, `cluster1-wf-diffuser`, the internal topics
are:

```text
cluster1-wf-diffuser-global-metadata-changelog
cluster1-wf-diffuser-wfrun-store-changelog
```

The global metadata store itself is restored from
`{application.id}-global-metadata-changelog`; it does not have a separate
changelog topic. The output topic list is dynamic: add one output topic for
every entity name configured under the tenant. If Kafka topic auto-creation is
disabled, provision the output topics before the diffuser produces to them.

## 5. Full JSON schemas by entity

These are generic JSON Schema Draft 07 contracts for each streamlet entity,
which is supported by Saddle.
Topic-specific schemas can refine `columns` for a WfSpec or `results` for a
UserTaskDef. The included example topic schemas are in
[`examples/schemas/`](../../examples/schemas/).

### Workflow streamlet

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Workflow Streamlet",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "LittleHorse WfRun ID."
    },
    "status": {
      "type": "string",
      "description": "LittleHorse WfRun status; it can be absent on a variable-only update that arrives before a WfRun record."
    },
    "columns": {
      "type": "object",
      "description": "Public WfSpec variables keyed by variable name.",
      "additionalProperties": true
    },
    "createdAt": {
      "type": ["string", "null"],
      "format": "date-time",
      "description": "WfRun start time; it can be absent before a WfRun snapshot arrives."
    },
    "updatedAt": {
      "type": ["string", "null"],
      "format": "date-time",
      "description": "Timestamp of the source OutputTopicRecord."
    }
  },
  "required": ["id", "columns", "updatedAt"],
  "additionalProperties": true
}
```

### User-task streamlet

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "User Task Streamlet",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Composite key: {wfRunId}/{userTaskGuid}."
    },
    "wfRunId": { "type": "string" },
    "userTaskDefName": { "type": "string" },
    "userTaskDefVersion": { "type": "integer" },
    "status": { "type": "string" },
    "userId": { "type": ["string", "null"] },
    "userGroup": { "type": ["string", "null"] },
    "results": {
      "type": "object",
      "description": "User-task result fields keyed by field name.",
      "additionalProperties": true
    },
    "createdAt": {
      "type": ["string", "null"],
      "format": "date-time"
    }
  },
  "required": [
    "id",
    "wfRunId",
    "userTaskDefName",
    "userTaskDefVersion",
    "status",
    "userId",
    "userGroup",
    "results",
    "createdAt"
  ],
  "additionalProperties": true
}
```

### External-event streamlet

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "External Event Streamlet",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Composite key: {wfRunId}/{externalEventDefName}/{guid}."
    },
    "wfRunId": { "type": "string" },
    "content": {
      "description": "External-event payload. Any valid JSON value is allowed."
    },
    "claimed": { "type": "boolean" },
    "createdAt": {
      "type": ["string", "null"],
      "format": "date-time"
    }
  },
  "required": ["id", "wfRunId", "content", "claimed", "createdAt"],
  "additionalProperties": true
}
```

### Workflow-event streamlet

```json
{
  "$schema": "http://json-schema.org/draft-07/schema#",
  "title": "Workflow Event Streamlet",
  "type": "object",
  "properties": {
    "id": {
      "type": "string",
      "description": "Composite key: {wfRunId}/{workflowEventDefName}/{number}."
    },
    "wfRunId": { "type": "string" },
    "workflowEventDefName": { "type": "string" },
    "content": {
      "description": "Workflow-event payload. Any valid JSON value is allowed."
    },
    "createdAt": {
      "type": ["string", "null"],
      "format": "date-time"
    }
  },
  "required": [
    "id",
    "wfRunId",
    "workflowEventDefName",
    "content",
    "createdAt"
  ],
  "additionalProperties": true
}
```

## 6. Application configuration

Only the cluster ID and tenant streamlet configuration determine the streamlet
input subscriptions and output topics.

```yaml
app:
  # Prefix used for input and output topics.
  lh-cluster-id: cluster1

  tenants:
    - name: default
      # Each configured entry enables the matching streamlet for this tenant.
      wf-specs:
        - output-topic
      user-task-defs:
        - it-request
      external-event-defs:
        - name-event
      workflow-event-defs:
        - sleep-done
```

| Property | Purpose |
| --- | --- |
| `app.lh-cluster-id` | Required cluster-topic prefix. It is used to build both input patterns and output topic names. The default application configuration uses `cluster1`. |
| `app.tenants[].name` | Tenant to subscribe to. |
| `app.tenants[].wf-specs` | WfSpec names for which workflow streamlets are emitted. |
| `app.tenants[].user-task-defs` | UserTaskDef names for which user-task streamlets are emitted. |
| `app.tenants[].external-event-defs` | ExternalEventDef names for which external-event streamlets are emitted. |
| `app.tenants[].workflow-event-defs` | WorkflowEventDef names for which workflow-event streamlets are emitted. |

Each optional list independently enables that entity type for the tenant. If a
list is absent, that streamlet type is disabled for that tenant. If no tenants
are configured, the application creates an empty topology.
