# Metadata Analysis: Schema Derivation from LH Metadata

This document analyzes the metadata samples from a live LittleHorse cluster (`data-samples/metadata.txt`) to establish how the Workflow Diffuser derives output Streamlet schemas from each metadata type. It serves as a reference for implementers.

- [Metadata Analysis: Schema Derivation from LH Metadata](#metadata-analysis-schema-derivation-from-lh-metadata)
  - [1. Metadata Topic Structure](#1-metadata-topic-structure)
  - [2. Metadata Types Inventory](#2-metadata-types-inventory)
    - [TaskDef (6 entries)](#taskdef-6-entries)
    - [WfSpec (5 entries)](#wfspec-5-entries)
    - [ExternalEventDef (1 entry)](#externaleventdef-1-entry)
    - [UserTaskDef (2 entries)](#usertaskdef-2-entries)
    - [WorkflowEventDef (1 entry)](#workfloweventdef-1-entry)
  - [3. Metadata Relationships (Entity Graph)](#3-metadata-relationships-entity-graph)
    - [WfSpec to TaskDef](#wfspec-to-taskdef)
    - [WfSpec to ExternalEventDef](#wfspec-to-externaleventdef)
    - [WfSpec to UserTaskDef](#wfspec-to-usertaskdef)
    - [WfSpec to WorkflowEventDef](#wfspec-to-workfloweventdef)
    - [UserTask Actions to TaskDef](#usertask-actions-to-taskdef)
    - [WfSpec Exception Handlers to TaskDef](#wfspec-exception-handlers-to-taskdef)
    - [Dependency Diagram](#dependency-diagram)
    - [Implications for the WFD](#implications-for-the-wfd)
  - [4. Schema Derivation Rules per Streamlet Type](#4-schema-derivation-rules-per-streamlet-type)
    - [4.1 WfSpec Streamlets](#41-wfspec-streamlets)
    - [4.2 ExternalEventDef Streamlets](#42-externaleventdef-streamlets)
    - [4.3 UserTaskDef Streamlets](#43-usertaskdef-streamlets)
    - [4.4 WorkflowEventDef Streamlets](#44-workfloweventdef-streamlets)
  - [5. LH-to-JSON Type Mapping Table](#5-lh-to-json-type-mapping-table)
  - [6. Worked Examples](#6-worked-examples)
    - [6.1 WfSpec: `output-topic` (v0.0)](#61-wfspec-output-topic-v00)
    - [6.2 WfSpec: `example-variables` (v0.0)](#62-wfspec-example-variables-v00)
    - [6.3 WfSpec: `example-external-event` (v0.0)](#63-wfspec-example-external-event-v00)
    - [6.4 WfSpec: `it-request` (v0.0)](#64-wfspec-it-request-v00)
    - [6.5 WfSpec: `await-wf-event` (v0.0)](#65-wfspec-await-wf-event-v00)
    - [6.6 ExternalEventDef: `name-event`](#66-externaleventdef-name-event)
    - [6.7 UserTaskDef: `it-request` (v0)](#67-usertaskdef-it-request-v0)
    - [6.8 UserTaskDef: `approve-it-request` (v0)](#68-usertaskdef-approve-it-request-v0)
    - [6.9 WorkflowEventDef: `sleep-done`](#69-workfloweventdef-sleep-done)

---

## 1. Metadata Topic Structure

The metadata topic is a **single-partition, compacted** Kafka topic that acts as a key-value store of the latest metadata state. Each record consists of:

**Key format** (plaintext string):
- For unversioned types: `{TYPE}/{name}` — e.g. `TASK_DEF/greet`, `EXTERNAL_EVENT_DEF/name-event`
- For versioned types: `{TYPE}/{name}/{majorVersion}/{revision}` — e.g. `WF_SPEC/output-topic/00000/00000`
- For simply-versioned types: `{TYPE}/{name}/{version}` — e.g. `USER_TASK_DEF/it-request/00000`

**Value format**: JSON-encoded `MetadataOutputTopicRecord` protobuf. Exactly one `oneof` field is set per record:
- `wfSpec` — WfSpec metadata
- `taskDef` — TaskDef metadata
- `externalEventDef` — ExternalEventDef metadata
- `workflowEventDef` — WorkflowEventDef metadata
- `userTaskDef` — UserTaskDef metadata
- `structDef` — StructDef metadata (not present in our samples)

**Topic naming**: `{LHS_CLUSTER_ID}_{tenant}_metadata-output` (e.g. `cluster1_default_metadata`).

Because the topic is compacted, consumers see only the latest version of each metadata entry. This allows Kafka Streams GlobalKTable-based joins with execution data.

---

## 2. Metadata Types Inventory

The following metadata was extracted from `data-samples/metadata.txt`, which was read from the topic `cluster1_default_metadata`.

### TaskDef (6 entries)

| Name                 | Input Vars                                       | Return Type         |
|----------------------|--------------------------------------------------|---------------------|
| `greet`              | `arg0`: STR                                      | STR                 |
| `sentiment-analysis` | (unnamed): STR (masked)                          | DOUBLE              |
| `process-text`       | (unnamed): STR (masked), `arg1`: DOUBLE, `arg2`: BOOL, `arg3`: INT | JSON_OBJ (masked) |
| `send`               | (unnamed): JSON_OBJ (masked)                     | STR                 |
| `ask-for-name`       | _(none)_                                         | STR                 |
| `send-email`         | `arg0`: STR, `arg1`: STR                         | _(void)_            |

### WfSpec (5 entries)

| Name                     | Version | Frozen Vars              | Entrypoint Vars                                                                 |
|--------------------------|---------|--------------------------|---------------------------------------------------------------------------------|
| `output-topic`           | 0.0     | `public-greeting` (PUBLIC), `input-name` (PUBLIC) | `input-name` (PUBLIC), `ignored` (PRIVATE), `public-greeting` (PUBLIC) |
| `example-variables`      | 0.0     | _(none)_                 | `input-text` (PRIVATE, masked), `add-length` (PRIVATE), `user-id` (PRIVATE), `sentiment-score` (PRIVATE), `processed-result` (PRIVATE, masked) |
| `example-external-event` | 0.0     | _(none)_                 | `name` (PRIVATE)                                                                |
| `it-request`             | 0.0     | _(none)_                 | `user-id` (PRIVATE), `it-request` (PRIVATE), `is-approved` (PRIVATE)            |
| `await-wf-event`         | 0.0     | `sleep-time` (PRIVATE, required) | `sleep-time` (PRIVATE, required)                                         |

### ExternalEventDef (1 entry)

| Name         | Type Information |
|--------------|-----------------|
| `name-event` | _(none)_ — no `type_information` field; schema is untyped |

### UserTaskDef (2 entries)

| Name                  | Version | Fields                                                              |
|-----------------------|---------|---------------------------------------------------------------------|
| `it-request`          | 0       | `requestedItem` (STR, required), `justification` (STR, required)    |
| `approve-it-request`  | 0       | `isApproved` (BOOL, required)                                       |

### WorkflowEventDef (1 entry)

| Name         | Content Type |
|--------------|-------------|
| `sleep-done` | _(void)_ — empty `contentType` field; payload type is unspecified |

---

## 3. Metadata Relationships (Entity Graph)

This section traces which WfSpecs reference which other metadata, extracted by walking each WfSpec's thread specs and node definitions.

### WfSpec to TaskDef

Extracted from `TaskNode.taskDefId.name` across all thread specs:

| WfSpec                   | TaskDefs Referenced                    | Nodes                                                           |
|--------------------------|----------------------------------------|-----------------------------------------------------------------|
| `output-topic`           | `greet`                                | `1-greet-TASK`                                                  |
| `example-variables`      | `sentiment-analysis`, `process-text`, `send` | `1-sentiment-analysis-TASK`, `2-process-text-TASK`, `3-send-TASK` |
| `example-external-event` | `ask-for-name`, `greet`                | `1-ask-for-name-TASK`, `3-greet-TASK`                           |
| `it-request`             | `send-email`                           | `4-send-email-TASK`, `6-send-email-TASK` (entrypoint) + `1-send-email-TASK` (exception handler) |
| `await-wf-event`         | _(none)_                               | _(only Sleep and ThrowEvent nodes)_                             |

### WfSpec to ExternalEventDef

Extracted from `ExternalEventNode.externalEventDefId.name`:

| WfSpec                   | ExternalEventDefs Referenced |
|--------------------------|------------------------------|
| `example-external-event` | `name-event`                 |

### WfSpec to UserTaskDef

Extracted from `UserTaskNode.userTaskDefName` (+ `userTaskDefVersion`):

| WfSpec       | UserTaskDefs Referenced                        |
|--------------|------------------------------------------------|
| `it-request` | `it-request` (v0), `approve-it-request` (v0)  |

### WfSpec to WorkflowEventDef

Extracted from `ThrowEventNode.eventDefId.name`:

| WfSpec           | WorkflowEventDefs Referenced |
|------------------|------------------------------|
| `await-wf-event` | `sleep-done`                 |

### UserTask Actions to TaskDef

Extracted from `UTActionTrigger.task.task.taskDefId.name` inside `UserTaskNode.actions`:

| WfSpec       | UserTaskNode             | Action Hook  | TaskDef Referenced |
|--------------|--------------------------|--------------|--------------------|
| `it-request` | `2-approve-it-request-USER_TASK` | `ON_ARRIVAL` | `send-email`       |

The `approve-it-request` user task has an `ON_ARRIVAL` action that schedules a `send-email` task (to notify the finance team of a new assigned task).

### WfSpec Exception Handlers to TaskDef

Extracted from `FailureHandlerDef.handlerSpecName` → handler thread spec → `TaskNode.taskDefId.name`:

| WfSpec       | Node with Handler               | Handler ThreadSpec                              | TaskDef Referenced |
|--------------|----------------------------------|-------------------------------------------------|--------------------|
| `it-request` | `1-it-request-USER_TASK`         | `exn-handler-1-it-request-USER_TASK-null`       | `send-email`       |

When the `it-request` user task fails with an exception, the handler sends an email notification ("Task cancelled").

### Dependency Diagram

```
                         ┌─────────────────┐
                         │    TaskDefs      │
                         │  (not Streamlet  │
                         │   sources)       │
                         └────────▲─────────┘
                                  │ referenced by
              ┌───────────────────┼───────────────────────┐
              │                   │                       │
    ┌─────────┴──────────┐       │            ┌──────────┴──────────┐
    │     WfSpecs         │       │            │  UTActionTriggers   │
    │  (Streamlet source: │───────┤            │  (inside UserTask   │
    │   WfRun entities)   │       │            │   nodes in WfSpecs) │
    └──┬──────┬───────┬───┘       │            └────────────────────┘
       │      │       │           │
       │      │       │    ┌──────┴────────┐
       │      │       └───▶│ ExternalEvent  │
       │      │            │    Defs        │
       │      │            │ (Streamlet src │
       │      │            │  + WfSpec dep) │
       │      │            └───────────────┘
       │      │
       │      │     ┌───────────────┐
       │      └────▶│ UserTaskDefs  │
       │            │ (Streamlet src│
       │            │  + WfSpec dep)│
       │            └───────────────┘
       │
       │     ┌──────────────────┐
       └────▶│ WorkflowEvent   │
             │    Defs          │
             │ (Streamlet src   │
             │  + WfSpec dep)   │
             └──────────────────┘
```

### Implications for the WFD

1. **TaskDefs are NOT Streamlet sources.** They are dependencies needed for enrichment (e.g. to understand what task a WfSpec is executing), but the WFD does not create a Streamlet for each TaskDef. The execution-topic `TaskRun` records are consumed and correlated with their parent WfRun, but TaskDefs themselves don't generate output Streamlets.

2. **ExternalEventDefs, UserTaskDefs, and WorkflowEventDefs are dual-purpose:** they are both dependencies of WfSpecs (referenced in WfSpec node definitions) AND independent Streamlet sources (each gets its own output Streamlet).

3. **Shared metadata across WfSpecs:** A single TaskDef (e.g. `greet`) can be referenced by multiple WfSpecs (`output-topic`, `example-external-event`). Similarly, `send-email` is referenced by `it-request` in three different contexts (entrypoint tasks, exception handler, and user task action trigger). The WFD's metadata store must support this N:M relationship.

4. **The metadata topic as a GlobalKTable:** Since the metadata topic is single-partition and compacted, it is ideal for use as a Kafka Streams GlobalKTable. The WFD can load all metadata at startup and keep it updated, then join incoming execution records against this store.

---

## 4. Schema Derivation Rules per Streamlet Type

### 4.1 WfSpec Streamlets

A WfSpec Streamlet represents each `WfRun` as an entity (a row in a table). The output schema is derived from the WfSpec's variable definitions.

**Algorithm:**

1. Collect all variables from `frozenVariables` and from the entrypoint thread's `variableDefs` (identified by `entrypointThreadName`). The `frozenVariables` are the canonical set — they define variables whose types are locked across revisions. The entrypoint `variableDefs` include the same variables plus any non-frozen ones.

2. **Filter by access level:** Only variables with `accessLevel == PUBLIC_VAR` become columns in the Streamlet output. Variables with `PRIVATE_VAR` or `INHERITED_VAR` are excluded.

   > Rationale: The Output Topic design specifies that only `PUBLIC_VAR` variables are journaled by default (see `docs/context/output-topic.md`, "Configuring What's Sent" section). The WFD consumes `Variable` records from the execution topic, and only public variables will appear there (unless overridden).

3. **Map each variable's type** to a JSON schema type using the mapping in [Section 5](#5-lh-to-json-type-mapping-table). The type is found in `varDef.typeDef.primitiveType` (or `varDef.typeDef.structDefId` for struct types).

4. **Handle the `masked` flag:** If `varDef.typeDef.masked == true`, the variable's value will be masked by the LH Server. The WFD should still include the column in the schema but note that runtime values will be redacted strings. Consider whether to mark the column as masked in the ApiCurio schema metadata.

5. **Handle `jsonIndexes`:** For `JSON_OBJ` or `JSON_ARR` variables, the `jsonIndexes` field defines sub-fields that are indexed. Each `JsonIndex` has a `fieldPath` (JSONPath format) and a `fieldType` (VariableType). These can be used to define nested properties in the JSON schema for the column. This enables downstream consumers to query specific fields within a JSON object variable.

6. **Produce the Streamlet record format:**
   ```json
   {
     "id": "<wfRunId>",
     "status": "<LHStatus>",
     "columns": {
       "<var-name-1>": <value>,
       "<var-name-2>": <value>
     },
     "createdAt": "<ISO-8601 timestamp>"
   }
   ```

**Additional variable metadata to consider:**
- `required`: Whether the variable must be provided as input when starting the WfRun. Affects whether the JSON schema field is required or optional.
- `searchable`: Whether the variable is indexed in the LH Server. Informational for the WFD; does not affect schema derivation.

### 4.2 ExternalEventDef Streamlets

An ExternalEventDef Streamlet captures each `ExternalEvent` as a record. The schema is derived from the ExternalEventDef's `type_information` field.

**Algorithm:**

1. Check if `type_information` is present (it is an `optional ReturnType` field).
2. **If present:** Extract `type_information.returnType` (a `TypeDefinition`). Map the type to a JSON schema type using the mapping in [Section 5](#5-lh-to-json-type-mapping-table).
3. **If absent:** The ExternalEventDef was created before LH v0.13.2 or has no type constraint. The Streamlet schema for the payload should be generic — accept any `VariableValue` (effectively `{}` / any type in JSON schema).

**Streamlet record format:**
```json
{
  "id": "<externalEventId>",
  "wfRunId": "<wfRunId>",
  "content": <payload>,
  "claimed": <boolean>,
  "createdAt": "<ISO-8601 timestamp>"
}
```

### 4.3 UserTaskDef Streamlets

A UserTaskDef Streamlet captures each `UserTaskRun` as a record. The schema is derived from the UserTaskDef's `fields` list.

**Algorithm:**

1. Walk the `fields` array in the `UserTaskDef`.
2. For each `UserTaskField`:
   - `name` → column name (also the key in the `results` map of a completed `UserTaskRun`)
   - `type` → map to JSON schema type using [Section 5](#5-lh-to-json-type-mapping-table) (limited to primitive types: `STR`, `BOOL`, `INT`, `DOUBLE`)
   - `required` → whether the field is required in the JSON schema
   - `displayName` and `description` → can be included as schema metadata/annotations

**Streamlet record format:**
```json
{
  "id": "<userTaskRunId>",
  "wfRunId": "<wfRunId>",
  "userTaskDefName": "<name>",
  "userTaskDefVersion": <version>,
  "status": "<UserTaskRunStatus>",
  "userId": "<userId or null>",
  "userGroup": "<userGroup or null>",
  "results": {
    "<field-name-1>": <value>,
    "<field-name-2>": <value>
  },
  "createdAt": "<ISO-8601 timestamp>"
}
```

### 4.4 WorkflowEventDef Streamlets

A WorkflowEventDef Streamlet captures each `WorkflowEvent` as a record. The schema is derived from the WorkflowEventDef's `contentType` field.

**Algorithm:**

1. Check `contentType` (a `ReturnType` field — not optional in protobuf, but may be empty/default).
2. **If `contentType.returnType` is present:** Extract the `TypeDefinition` and map to a JSON schema type.
3. **If `contentType` is empty/default:** The payload type is unspecified (void). The content field should be nullable or omitted in the Streamlet record.

**Streamlet record format:**
```json
{
  "id": "<workflowEventId>",
  "wfRunId": "<wfRunId>",
  "workflowEventDefName": "<name>",
  "content": <payload or null>,
  "createdAt": "<ISO-8601 timestamp>"
}
```

---

## 5. LH-to-JSON Type Mapping Table

The following table maps LittleHorse `VariableType` (protobuf enum) values to JSON Schema types. This mapping is used by all Streamlet types when deriving output schemas.

| LH `VariableType` | Protobuf Enum Value | `VariableValue` oneof field | JSON Schema Type | JSON Schema Format | Notes |
|-------------------|--------------------|-----------------------------|------------------|--------------------|-------|
| `JSON_OBJ`        | 0                  | `json_obj` (string)         | `object`         | —                  | Stored as serialized JSON string; parse to object |
| `JSON_ARR`        | 1                  | `json_arr` (string)         | `array`          | —                  | Stored as serialized JSON string; parse to array |
| `DOUBLE`          | 2                  | `double` (double)           | `number`         | `double`           | 64-bit IEEE 754 floating point |
| `BOOL`            | 3                  | `bool` (bool)               | `boolean`        | —                  | |
| `STR`             | 4                  | `str` (string)              | `string`         | —                  | |
| `INT`             | 5                  | `int` (int64)               | `integer`        | `int64`            | 64-bit signed integer |
| `BYTES`           | 6                  | `bytes` (bytes)             | `string`         | `byte`             | Base64-encoded in JSON |
| `WF_RUN_ID`       | 7                  | `wf_run_id` (WfRunId)       | `string`         | —                  | Serialized WfRunId (structured ID) |
| `TIMESTAMP`       | 8                  | `utc_timestamp` (Timestamp) | `string`         | `date-time`        | ISO 8601 UTC |

**Special cases:**
- **`masked == true`**: The LH Server replaces the actual value with a masked string (e.g. `"******"`). The JSON schema type remains the same, but runtime values will be redacted. The WFD should preserve the masking.
- **`JSON_OBJ` with `jsonIndexes`**: When a variable has `jsonIndexes`, the sub-fields are individually typed. These can be represented as `properties` in the JSON schema for the `object` type.
- **`StructDef` types**: When `TypeDefinition.structDefId` is set instead of `primitiveType`, the type is a user-defined struct. The WFD would need to look up the `StructDef` from the metadata store to derive the nested schema. (Not present in our samples.)
- **Void / unset `ReturnType`**: When `ReturnType.returnType` is absent, the value is always null/empty. Represent as `{"type": "null"}` or omit from the schema.

### UserTaskField Type Mapping

The `UserTaskField.type` field uses the same `VariableType` enum but is restricted to primitive types in practice:

| `UserTaskField.type` | JSON Schema Type |
|---------------------|------------------|
| `STR`               | `string`         |
| `INT`               | `integer`        |
| `DOUBLE`            | `number`         |
| `BOOL`              | `boolean`        |

---

## 6. Worked Examples

### 6.1 WfSpec: `output-topic` (v0.0)

**Source variables (union of `frozenVariables` and entrypoint `variableDefs`):**

| Variable           | Type  | Access Level  | Masked | Searchable | Required | jsonIndexes |
|--------------------|-------|---------------|--------|------------|----------|-------------|
| `input-name`       | STR   | `PUBLIC_VAR`  | false  | true       | false    | —           |
| `public-greeting`  | STR   | `PUBLIC_VAR`  | false  | false      | false    | —           |
| `ignored`          | STR   | `PRIVATE_VAR` | false  | false      | false    | —           |

**Filtering (PUBLIC_VAR only):**
- `input-name` → **included**
- `public-greeting` → **included**
- `ignored` → **excluded** (PRIVATE_VAR)

**Derived Streamlet schema:**
```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string", "description": "WfRun ID" },
    "status": { "type": "string", "enum": ["STARTING", "RUNNING", "COMPLETED", "HALTING", "HALTED", "ERROR", "EXCEPTION"] },
    "columns": {
      "type": "object",
      "properties": {
        "input-name": { "type": "string" },
        "public-greeting": { "type": "string" }
      }
    },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

**Example output record:**
```json
{
  "id": "abc123",
  "status": "COMPLETED",
  "columns": {
    "input-name": "Obi-Wan",
    "public-greeting": "Hello there, Obi-Wan!"
  },
  "createdAt": "2026-02-10T15:05:00.000Z"
}
```

### 6.2 WfSpec: `example-variables` (v0.0)

**Source variables (entrypoint `variableDefs` only — no frozen variables):**

| Variable           | Type     | Access Level  | Masked | jsonIndexes                        |
|--------------------|----------|---------------|--------|------------------------------------|
| `input-text`       | STR      | `PRIVATE_VAR` | true   | —                                  |
| `add-length`       | BOOL     | `PRIVATE_VAR` | false  | —                                  |
| `user-id`          | INT      | `PRIVATE_VAR` | false  | —                                  |
| `sentiment-score`  | DOUBLE   | `PRIVATE_VAR` | false  | —                                  |
| `processed-result` | JSON_OBJ | `PRIVATE_VAR` | true   | `$.sentimentScore` → DOUBLE        |

**Filtering:** All variables are `PRIVATE_VAR` → **no columns**.

**Derived Streamlet schema:**
```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string" },
    "status": { "type": "string", "enum": ["STARTING", "RUNNING", "COMPLETED", "HALTING", "HALTED", "ERROR", "EXCEPTION"] },
    "columns": { "type": "object", "properties": {} },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

This WfSpec produces a Streamlet with **no variable columns** — only the `id`, `status`, and `createdAt` envelope fields. This is because none of its variables are declared as `PUBLIC_VAR`.

> **Note for implementers:** This is a valid but minimal Streamlet. It would still capture WfRun lifecycle events (status changes). If the WfSpec author later promotes variables to `PUBLIC_VAR`, the schema would evolve to include those columns.

### 6.3 WfSpec: `example-external-event` (v0.0)

**Source variables:**

| Variable | Type | Access Level  | Masked |
|----------|------|---------------|--------|
| `name`   | STR  | `PRIVATE_VAR` | false  |

**Filtering:** All `PRIVATE_VAR` → **no columns**.

Same as `example-variables`: a Streamlet with only envelope fields and no variable columns.

### 6.4 WfSpec: `it-request` (v0.0)

**Source variables (entrypoint `variableDefs`):**

| Variable      | Type     | Access Level  | Masked |
|---------------|----------|---------------|--------|
| `user-id`     | STR      | `PRIVATE_VAR` | false  |
| `it-request`  | JSON_OBJ | `PRIVATE_VAR` | false  |
| `is-approved` | BOOL     | `PRIVATE_VAR` | false  |

**Filtering:** All `PRIVATE_VAR` → **no columns**.

Again, envelope-only Streamlet. Despite being a complex workflow with user tasks, branching, and exception handling, the WfSpec author did not mark any variables as public.

### 6.5 WfSpec: `await-wf-event` (v0.0)

**Source variables:**

| Variable     | Type | Access Level  | Masked | Required |
|--------------|------|---------------|--------|----------|
| `sleep-time` | INT  | `PRIVATE_VAR` | false  | true     |

**Filtering:** All `PRIVATE_VAR` → **no columns**.

Envelope-only Streamlet. The `sleep-time` variable is `required` (must be provided as input to start the WfRun) but is private.

### 6.6 ExternalEventDef: `name-event`

**Metadata:**
```json
{
  "externalEventDef": {
    "id": { "name": "name-event" },
    "createdAt": "2026-02-10T15:14:00.970Z",
    "retentionPolicy": {}
  }
}
```

**Schema derivation:** No `type_information` field → the payload type is unspecified.

**Derived Streamlet schema:**
```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string", "description": "ExternalEvent ID" },
    "wfRunId": { "type": "string" },
    "content": { "description": "Untyped payload — any VariableValue" },
    "claimed": { "type": "boolean" },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

The `content` field has no type constraint — it could be a string, number, object, etc. The WFD should use a permissive schema or `{}` (any type).

### 6.7 UserTaskDef: `it-request` (v0)

**Metadata:**
```json
{
  "userTaskDef": {
    "name": "it-request",
    "version": 0,
    "fields": [
      { "name": "requestedItem", "type": "STR", "description": "The item you are requesting.", "displayName": "Your Request", "required": true },
      { "name": "justification", "type": "STR", "description": "Why you need this request.", "displayName": "Request Justification", "required": true }
    ]
  }
}
```

**Derived Streamlet schema (for `results` in the record):**
```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string", "description": "UserTaskRun ID" },
    "wfRunId": { "type": "string" },
    "userTaskDefName": { "const": "it-request" },
    "userTaskDefVersion": { "const": 0 },
    "status": { "type": "string", "enum": ["UNASSIGNED", "ASSIGNED", "DONE", "CANCELLED"] },
    "userId": { "type": ["string", "null"] },
    "userGroup": { "type": ["string", "null"] },
    "results": {
      "type": "object",
      "properties": {
        "requestedItem": { "type": "string", "description": "The item you are requesting." },
        "justification": { "type": "string", "description": "Why you need this request." }
      },
      "required": ["requestedItem", "justification"]
    },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

### 6.8 UserTaskDef: `approve-it-request` (v0)

**Metadata:**
```json
{
  "userTaskDef": {
    "name": "approve-it-request",
    "version": 0,
    "fields": [
      { "name": "isApproved", "type": "BOOL", "description": "Reply 'true' if this is an acceptable request.", "displayName": "Approved?", "required": true }
    ]
  }
}
```

**Derived Streamlet schema (for `results`):**
```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string" },
    "wfRunId": { "type": "string" },
    "userTaskDefName": { "const": "approve-it-request" },
    "userTaskDefVersion": { "const": 0 },
    "status": { "type": "string", "enum": ["UNASSIGNED", "ASSIGNED", "DONE", "CANCELLED"] },
    "userId": { "type": ["string", "null"] },
    "userGroup": { "type": ["string", "null"] },
    "results": {
      "type": "object",
      "properties": {
        "isApproved": { "type": "boolean", "description": "Reply 'true' if this is an acceptable request." }
      },
      "required": ["isApproved"]
    },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

### 6.9 WorkflowEventDef: `sleep-done`

**Metadata:**
```json
{
  "workflowEventDef": {
    "id": { "name": "sleep-done" },
    "createdAt": "2026-02-10T15:15:08.502Z",
    "contentType": {}
  }
}
```

**Schema derivation:** `contentType` is empty (no `returnType` inside) → void payload.

**Derived Streamlet schema:**
```json
{
  "type": "object",
  "properties": {
    "id": { "type": "string", "description": "WorkflowEvent ID" },
    "wfRunId": { "type": "string" },
    "workflowEventDefName": { "const": "sleep-done" },
    "content": { "type": "null", "description": "Void — no payload" },
    "createdAt": { "type": "string", "format": "date-time" }
  }
}
```

In the `await-wf-event` WfSpec, the `ThrowEventNode` sends a literal string `"hello there!"` as content, despite the WorkflowEventDef having a void content type. This is a quirk of the current LH metadata — the actual runtime value may be a string, but the metadata-level type information says void. The WFD should handle this gracefully (e.g. accept any content when the type is void, or discard the content if strict typing is required).
