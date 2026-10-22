# Execution Topic Analysis: Record Sequences and Streamlet Transformation

This document analyzes the execution samples from a live LittleHorse cluster (`data-samples/execution.txt`) to establish how the Workflow Diffuser processes `OutputTopicRecord` messages from the execution topic into Streamlet output. It traces event sequences per wfRunId and documents the aggregation semantics needed for each Streamlet type.

- [1. Execution Topic Structure](#1-execution-topic-structure)
- [2. Record Types Inventory](#2-record-types-inventory)
- [3. WfRun Event Sequences (per wfRunId)](#3-wfrun-event-sequences-per-wfrunid)
  - [3.1 `await-wf-event` WfSpec](#31-await-wf-event-wfspec)
  - [3.2 `output-topic` WfSpec](#32-output-topic-wfspec)
  - [3.3 `example-variables` WfSpec](#33-example-variables-wfspec)
  - [3.4 `example-external-event` WfSpec](#34-example-external-event-wfspec)
  - [3.5 `it-request` WfSpec](#35-it-request-wfspec)
- [4. Streamlet Transformation Rules](#4-streamlet-transformation-rules)
  - [4.1 WfSpec Streamlets (stateful aggregation)](#41-wfspec-streamlets-stateful-aggregation)
  - [4.2 ExternalEventDef Streamlets (stateless)](#42-externaleventdef-streamlets-stateless)
  - [4.3 UserTaskDef Streamlets (stateless per-record)](#43-usertaskdef-streamlets-stateless-per-record)
  - [4.4 WorkflowEventDef Streamlets (stateless)](#44-workfloweventdef-streamlets-stateless)
- [5. Key Observations for Implementation](#5-key-observations-for-implementation)
- [6. WfSpec Streamlet Worked Example](#6-wfspec-streamlet-worked-example)
- [7. UserTaskDef Streamlet Worked Example](#7-usertaskdef-streamlet-worked-example)

---

## 1. Execution Topic Structure

**Topic**: `{LHS_CLUSTER_ID}_{tenant}_execution` (e.g. `cluster1_default_execution`)

| Property | Value |
|----------|-------|
| Partitions | Multi-partition |
| Cleanup policy | `delete` (non-compacted) |
| Key format | wfRunId hex string (e.g. `26ab000bba79445dbb85f4f5ceff7a30`) |
| Value format | JSON-encoded `OutputTopicRecord` protobuf |
| Ordering | Records with the same key arrive in order within a partition |

**Key**: The Kafka record key is the wfRunId — the same key for ALL record types belonging to the same WfRun (wfRun, taskRun, variable, externalEvent, workflowEvent, userTaskRun). This means all records for a given WfRun land in the same partition, guaranteeing ordering.

**Value**: An `OutputTopicRecord` with a `timestamp` field and a `oneof payload` containing one of 8 types:

```protobuf
message OutputTopicRecord {
    google.protobuf.Timestamp timestamp = 1;
    oneof payload {
        TaskRun task_run = 2;
        WorkflowEvent workflow_event = 3;
        WfRun wf_run = 4;
        UserTaskRun user_task_run = 5;
        Variable variable = 6;
        ExternalEvent external_event = 7;
        CorrelatedEvent correlated_event = 8;
        Checkpoint task_checkpoint = 9;
    }
}
```

---

## 2. Record Types Inventory

The following record types and counts were extracted from `data-samples/execution.txt` (topic `cluster1_default_execution`, 64 records total across 9 distinct wfRunIds):

| Payload Type      | Count | Description |
|-------------------|-------|-------------|
| **taskRun**       | 27    | TaskRun lifecycle snapshots (TASK_SCHEDULED → TASK_RUNNING → TASK_SUCCESS / TASK_TIMEOUT) |
| **wfRun**         | 18    | WfRun status snapshots (RUNNING → COMPLETED / ERROR) |
| **userTaskRun**   | 11    | UserTaskRun lifecycle snapshots (ASSIGNED → UNASSIGNED → DONE, with intermediate events) |
| **variable**      | 4     | Variable value changes (only `PUBLIC_VAR` variables appear) |
| **workflowEvent** | 3     | WorkflowEvent emissions |
| **externalEvent** | 1     | ExternalEvent arrivals |

**Not observed in samples**: `correlatedEvent`, `checkpoint` — defined in the proto but not exercised by our test workflows.

---

## 3. WfRun Event Sequences (per wfRunId)

### 3.1 `await-wf-event` WfSpec

The simplest pattern: WfRun starts, a sleep completes (emitting a WorkflowEvent), WfRun completes. Three runs observed.

#### wfRunId `26ab000b` (3 records)

| # | Payload Type    | Key Fields | Timestamp | State / Notes |
|---|-----------------|------------|-----------|---------------|
| 1 | wfRun           | wfSpecId: `await-wf-event/0/0` | `T15:16:29.693Z` | status: **RUNNING**, startTime: T15:16:29.693Z |
| 2 | workflowEvent   | workflowEventDefId: `sleep-done`, number: 0 | `T15:16:30.715Z` | content: `{"str": "hello there!"}`, createdAt: T15:16:31.164Z |
| 3 | wfRun           | wfSpecId: `await-wf-event/0/0` | `T15:16:30.715Z` | status: **COMPLETED**, endTime: T15:16:30.715Z |

#### wfRunId `8c9656e9` (3 records)

| # | Payload Type    | Key Fields | Timestamp | State / Notes |
|---|-----------------|------------|-----------|---------------|
| 1 | wfRun           | wfSpecId: `await-wf-event/0/0` | `T16:09:06.593Z` | status: **RUNNING** |
| 2 | workflowEvent   | workflowEventDefId: `sleep-done`, number: 0 | `T16:09:07.602Z` | content: `{"str": "hello there!"}`, createdAt: T16:09:07.653Z |
| 3 | wfRun           | wfSpecId: `await-wf-event/0/0` | `T16:09:07.602Z` | status: **COMPLETED** |

#### wfRunId `e8c71772` (3 records)

| # | Payload Type    | Key Fields | Timestamp | State / Notes |
|---|-----------------|------------|-----------|---------------|
| 1 | wfRun           | wfSpecId: `await-wf-event/0/0` | `T16:09:29.690Z` | status: **RUNNING** |
| 2 | workflowEvent   | workflowEventDefId: `sleep-done`, number: 0 | `T16:09:31.695Z` | content: `{"str": "hello there!"}`, createdAt: T16:09:31.768Z |
| 3 | wfRun           | wfSpecId: `await-wf-event/0/0` | `T16:09:31.695Z` | status: **COMPLETED** |

**Pattern**: `wfRun(RUNNING)` → `workflowEvent(sleep-done)` → `wfRun(COMPLETED)`. No tasks, no variables. The workflowEvent and the final wfRun share the same `timestamp` (processed in the same LH Command).

---

### 3.2 `output-topic` WfSpec

Demonstrates public variables and task timeout. Two runs observed; both end in ERROR because the `greet` task times out after 60s.

#### wfRunId `2f5fa647` (7 records)

| # | Payload Type | Key Fields | Timestamp | State / Notes |
|---|--------------|------------|-----------|---------------|
| 1 | wfRun        | wfSpecId: `output-topic/0/0` | `T16:04:33.531Z` | status: **RUNNING**, startTime: T16:04:33.531Z |
| 2 | taskRun      | taskDefId: `greet`, taskGuid: `0-1` | `T16:04:33.531Z` | status: **TASK_SCHEDULED**, source: taskNode |
| 3 | variable     | name: `input-name`, threadRunNumber: 0 | `T16:04:33.531Z` | value: `{"str": "Colt"}`, wfSpecId: `output-topic/0/0` |
| 4 | variable     | name: `public-greeting`, threadRunNumber: 0 | `T16:04:33.531Z` | value: `{}` (null/unset), wfSpecId: `output-topic/0/0` |
| 5 | taskRun      | taskDefId: `greet`, taskGuid: `0-1` | `T16:04:33.619Z` | status: **TASK_RUNNING**, worker picked up |
| 6 | taskRun      | taskDefId: `greet`, taskGuid: `0-1` | `T16:05:33.642Z` | status: **TASK_TIMEOUT** (~60s after schedule) |
| 7 | wfRun        | wfSpecId: `output-topic/0/0` | `T16:05:33.642Z` | status: **ERROR**, errorMessage: "Task timed out" |

**Observations**:
- Records 1-4 share the same timestamp (batch within a single LH Command — the `RunWf` command).
- Variable records include `wfSpecId` — enabling routing without a metadata join.
- The `public-greeting` variable has an empty value `{}` — this represents null/unset in the protobuf `VariableValue` oneof.

#### wfRunId `6967816b` (7 records)

| # | Payload Type | Key Fields | Timestamp | State / Notes |
|---|--------------|------------|-----------|---------------|
| 1 | wfRun        | wfSpecId: `output-topic/0/0` | `T16:04:41.594Z` | status: **RUNNING** |
| 2 | taskRun      | taskDefId: `greet`, taskGuid: `0-1` | `T16:04:41.594Z` | status: **TASK_SCHEDULED** |
| 3 | variable     | name: `input-name` | `T16:04:41.594Z` | value: `{"str": "Hartmut"}` |
| 4 | variable     | name: `public-greeting` | `T16:04:41.594Z` | value: `{}` (null/unset) |
| 5 | taskRun      | taskDefId: `greet`, taskGuid: `0-1` | `T16:04:41.601Z` | status: **TASK_RUNNING** |
| 6 | taskRun      | taskDefId: `greet`, taskGuid: `0-1` | `T16:05:41.603Z` | status: **TASK_TIMEOUT** |
| 7 | wfRun        | wfSpecId: `output-topic/0/0` | `T16:05:41.603Z` | status: **ERROR** |

Same pattern as `2f5fa647`. Both `output-topic` runs fail with task timeout.

---

### 3.3 `example-variables` WfSpec

A multi-task pipeline with all private variables. One run observed.

#### wfRunId `f6865602` (11 records)

| #  | Payload Type | Key Fields | Timestamp | State / Notes |
|----|--------------|------------|-----------|---------------|
| 1  | wfRun        | wfSpecId: `example-variables/0/0` | `T16:05:24.214Z` | status: **RUNNING** |
| 2  | taskRun      | taskDefId: `sentiment-analysis`, taskGuid: `0-1` | `T16:05:24.214Z` | status: **TASK_SCHEDULED** |
| 3  | taskRun      | taskDefId: `sentiment-analysis`, taskGuid: `0-1` | `T16:05:24.227Z` | status: **TASK_RUNNING** |
| 4  | taskRun      | taskDefId: `sentiment-analysis`, taskGuid: `0-1` | `T16:05:24.261Z` | status: **TASK_SUCCESS**, output: `{"double": 84.68}` |
| 5  | taskRun      | taskDefId: `process-text`, taskGuid: `0-2` | `T16:05:24.261Z` | status: **TASK_SCHEDULED** |
| 6  | taskRun      | taskDefId: `process-text`, taskGuid: `0-2` | `T16:05:24.281Z` | status: **TASK_RUNNING** |
| 7  | taskRun      | taskDefId: `process-text`, taskGuid: `0-2` | `T16:05:24.310Z` | status: **TASK_SUCCESS**, output: JSON_OBJ (masked) |
| 8  | taskRun      | taskDefId: `send`, taskGuid: `0-3` | `T16:05:24.310Z` | status: **TASK_SCHEDULED** |
| 9  | taskRun      | taskDefId: `send`, taskGuid: `0-3` | `T16:05:24.332Z` | status: **TASK_RUNNING** |
| 10 | taskRun      | taskDefId: `send`, taskGuid: `0-3` | `T16:05:24.344Z` | status: **TASK_SUCCESS** |
| 11 | wfRun        | wfSpecId: `example-variables/0/0` | `T16:05:24.344Z` | status: **COMPLETED** |

**Observations**:
- **No variable records emitted** — all variables in this WfSpec are `PRIVATE_VAR`, confirming that only `PUBLIC_VAR` variables appear on the execution topic.
- Three sequential tasks form a pipeline: `sentiment-analysis` → `process-text` → `send`.
- Each task goes through SCHEDULED → RUNNING → SUCCESS lifecycle (3 records per task = 9 taskRun records).
- The entire WfRun completes in ~130ms.

---

### 3.4 `example-external-event` WfSpec

Demonstrates external event arrival mid-workflow. One run observed.

#### wfRunId `c788297c` (9 records)

| # | Payload Type    | Key Fields | Timestamp | State / Notes |
|---|-----------------|------------|-----------|---------------|
| 1 | wfRun           | wfSpecId: `example-external-event/0/0` | `T16:06:31.328Z` | status: **RUNNING** |
| 2 | taskRun          | taskDefId: `ask-for-name`, taskGuid: `0-1` | `T16:06:31.328Z` | status: **TASK_SCHEDULED** |
| 3 | taskRun          | taskDefId: `ask-for-name`, taskGuid: `0-1` | `T16:06:31.337Z` | status: **TASK_RUNNING** |
| 4 | taskRun          | taskDefId: `ask-for-name`, taskGuid: `0-1` | `T16:06:31.365Z` | status: **TASK_SUCCESS**, output: "Hi what's your name?" |
| 5 | taskRun          | taskDefId: `greet`, taskGuid: `0-3` | `T16:07:12.745Z` | status: **TASK_SCHEDULED** |
| 6 | externalEvent    | externalEventDefId: `name-event`, guid: `09481e55...` | `T16:07:12.745Z` | content: `{"str": "Obi-Wan"}`, claimed: true |
| 7 | taskRun          | taskDefId: `greet`, taskGuid: `0-3` | `T16:07:12.754Z` | status: **TASK_RUNNING** |
| 8 | taskRun          | taskDefId: `greet`, taskGuid: `0-3` | `T16:07:12.763Z` | status: **TASK_SUCCESS**, output: "Hello there, Obi-Wan" |
| 9 | wfRun            | wfSpecId: `example-external-event/0/0` | `T16:07:12.763Z` | status: **COMPLETED** |

**Observations**:
- The `ask-for-name` task completes at T16:06:31.365Z, then the workflow waits for the external event.
- ~41s later, the `name-event` ExternalEvent arrives at T16:07:12.745Z with content `"Obi-Wan"`.
- The externalEvent and the `greet` taskRun SCHEDULED share the same timestamp — the external event arrival triggers the next node in the same LH Command.
- The externalEvent record shows `claimed: true` — the event was immediately consumed by the waiting WfRun.

---

### 3.5 `it-request` WfSpec

The most complex pattern: user tasks with assignment lifecycle, UT action triggers, and approval workflow. Three runs observed.

#### wfRunId `3549a0f7` (3 records — incomplete)

| # | Payload Type  | Key Fields | Timestamp | State / Notes |
|---|---------------|------------|-----------|---------------|
| 1 | wfRun         | wfSpecId: `it-request/0/0` | `T16:10:08.848Z` | status: **RUNNING** |
| 2 | userTaskRun   | userTaskDefId: `it-request/0`, userTaskGuid: `0-1` | `T16:10:08.848Z` | status: **ASSIGNED**, userId: `anakin`, userGroup: `testGroup`, epoch: 1 |
| 3 | userTaskRun   | userTaskDefId: `it-request/0`, userTaskGuid: `0-1` | `T16:11:08.858Z` | status: **UNASSIGNED**, userGroup: `testGroup` (userId removed), epoch: 2 |

The `it-request` user task was assigned to `anakin` at T16:10:08, then unassigned ~60s later (assignment timeout). No further records — the WfRun is still waiting for the user task to be completed.

#### wfRunId `18f9b31a` (3 records — incomplete)

| # | Payload Type  | Key Fields | Timestamp | State / Notes |
|---|---------------|------------|-----------|---------------|
| 1 | wfRun         | wfSpecId: `it-request/0/0` | `T16:13:28.380Z` | status: **RUNNING** |
| 2 | userTaskRun   | userTaskDefId: `it-request/0`, userTaskGuid: `0-1` | `T16:13:28.380Z` | status: **ASSIGNED**, userId: `r2d2`, userGroup: `testGroup`, epoch: 1 |
| 3 | userTaskRun   | userTaskDefId: `it-request/0`, userTaskGuid: `0-1` | `T16:14:28.383Z` | status: **UNASSIGNED**, userGroup: `testGroup` (userId removed), epoch: 2 |

Same pattern as `3549a0f7` — assigned to `r2d2`, then unassigned after timeout. No completion.

#### wfRunId `c59560f5` (15 records — full lifecycle)

| #  | Payload Type  | Key Fields | Timestamp | State / Notes |
|----|---------------|------------|-----------|---------------|
| 1  | wfRun         | wfSpecId: `it-request/0/0` | `T16:16:26.748Z` | status: **RUNNING** |
| 2  | userTaskRun   | `it-request/0`, guid: `0-1` | `T16:16:26.748Z` | status: **ASSIGNED**, userId: `r2d2`, userGroup: `testGroup`, epoch: 1 |
| 3  | userTaskRun   | `it-request/0`, guid: `0-1` | `T16:17:26.752Z` | status: **UNASSIGNED**, userGroup: `testGroup`, epoch: 2 (assignment timeout) |
| 4  | userTaskRun   | `it-request/0`, guid: `0-1` | `T16:17:29.669Z` | status: **DONE**, userId: `r2d2`, results: `{requestedItem: "Test response", justification: "just because"}`, epoch: 3 |
| 5  | userTaskRun   | `approve-it-request/0`, guid: `0-2` | `T16:17:29.669Z` | status: **UNASSIGNED**, userGroup: `finance`, notes: "User r2d2 is requesting...", epoch: 1 |
| 6  | taskRun       | taskDefId: `send-email`, taskGuid: `ut-0-2-1` | `T16:17:31.714Z` | status: **TASK_SCHEDULED**, source: **userTaskTrigger** (ON_ARRIVAL action), input: "finance@gmail.com", "Hi finance team..." |
| 7  | userTaskRun   | `approve-it-request/0`, guid: `0-2` | `T16:17:31.714Z` | status: **UNASSIGNED**, userGroup: `finance`, epoch: 1, events now include `taskExecuted` |
| 8  | taskRun       | taskDefId: `send-email`, taskGuid: `ut-0-2-1` | `T16:17:32.103Z` | status: **TASK_RUNNING** |
| 9  | taskRun       | taskDefId: `send-email`, taskGuid: `ut-0-2-1` | `T16:17:32.135Z` | status: **TASK_SUCCESS** |
| 10 | userTaskRun   | `approve-it-request/0`, guid: `0-2` | `T16:19:03.087Z` | status: **ASSIGNED**, userId: `mace`, epoch: 2 (claimed by mace from finance group) |
| 11 | taskRun       | taskDefId: `send-email`, taskGuid: `0-4` | `T16:19:40.114Z` | status: **TASK_SCHEDULED**, source: **taskNode**, input: "r2d2", "Dear r2d2, your request...approved!" |
| 12 | userTaskRun   | `approve-it-request/0`, guid: `0-2` | `T16:19:40.114Z` | status: **DONE**, userId: `Lalalu`, results: `{isApproved: true}`, epoch: 3 |
| 13 | taskRun       | taskDefId: `send-email`, taskGuid: `0-4` | `T16:19:40.133Z` | status: **TASK_RUNNING** |
| 14 | taskRun       | taskDefId: `send-email`, taskGuid: `0-4` | `T16:19:40.143Z` | status: **TASK_SUCCESS** |
| 15 | wfRun         | wfSpecId: `it-request/0/0` | `T16:19:40.143Z` | status: **COMPLETED** |

**Observations**:
- **Two distinct user tasks** in sequence: `it-request` (0-1) → `approve-it-request` (0-2).
- `it-request` goes through ASSIGNED → UNASSIGNED → DONE cycle.
- `approve-it-request` goes through UNASSIGNED → UNASSIGNED (with taskExecuted) → ASSIGNED → DONE.
- The taskGuid `ut-0-2-1` prefix `ut-` indicates a task triggered by a **UserTask action** (ON_ARRIVAL trigger), distinguished from regular task node tasks (prefix is just the position like `0-4`).
- The `source.userTaskTrigger` field (record #6) vs `source.taskNode` (record #11) explicitly differentiates UT action-triggered tasks from regular task node tasks.
- UserTaskRun record #7 adds a `taskExecuted` event without changing status or epoch — the same UNASSIGNED status and epoch 1, but the events array grew.
- The `notes` field on `approve-it-request` records contains business context passed from the previous user task results.

---

## 4. Streamlet Transformation Rules

### 4.1 WfSpec Streamlets (stateful aggregation)

**Input records**: `wfRun` (for id, status, createdAt) + `variable` (for column values)

**Routing**: Filter by `wfRun.wfSpecId.name` or `variable.wfSpecId.name` matching a configured WfSpec name. Both record types include `wfSpecId`, so routing is possible without a metadata join.

**Aggregation semantics**: Stateful — maintain per-wfRunId state accumulating status + variable values. Emit an updated record on every relevant input record.

- On `wfRun` record: update `status`, `createdAt` (from `startTime`), and `updatedAt` (from `timestamp`). If `endTime` is present, use it.
- On `variable` record: update the corresponding column in `columns` with the variable's value. Update `updatedAt` from `timestamp`.
- On `taskRun` record: **skip** — taskRun records are not relevant for WfSpec Streamlets.

**Output format** (from design doc, including `updatedAt`):

```json
{
  "id": "<wfRunId>",
  "status": "<LHStatus>",
  "columns": {
    "<var-name-1>": "<value>",
    "<var-name-2>": "<value>"
  },
  "createdAt": "<ISO-8601 timestamp>",
  "updatedAt": "<ISO-8601 timestamp>"
}
```

**Concrete example**: wfRunId `2f5fa647` (`output-topic` WfSpec) produces 4 Streamlet emissions (from records #1, #3, #4, and #7 — skipping taskRun records #2, #5, #6). See [Section 6](#6-wfspec-streamlet-worked-example) for the full walk-through.

---

### 4.2 ExternalEventDef Streamlets (stateless)

**Input records**: `externalEvent` only

**Routing**: Filter by `externalEvent.id.externalEventDefId.name` matching a configured ExternalEventDef name.

**Aggregation semantics**: Stateless — each `externalEvent` record is transformed and forwarded directly.

**Output format** (from metadata-analysis.md):

```json
{
  "id": "<externalEventId>",
  "wfRunId": "<wfRunId>",
  "content": "<payload>",
  "claimed": "<boolean>",
  "createdAt": "<ISO-8601 timestamp>"
}
```

**Concrete example**: `name-event` from wfRunId `c788297c`:

```json
{
  "id": "c788297c849347609500f553eaf3b335/name-event/09481e551f6f44b2b0acbbd4abbd71a1",
  "wfRunId": "c788297c849347609500f553eaf3b335",
  "content": "Obi-Wan",
  "claimed": true,
  "createdAt": "2026-02-10T16:07:12.745Z"
}
```

The `id` is composed from `wfRunId/externalEventDefId.name/guid`. The `content` field extracts the value from the `VariableValue` oneof (here `str: "Obi-Wan"` → `"Obi-Wan"`).

---

### 4.3 UserTaskDef Streamlets (stateless per-record)

**Input records**: `userTaskRun` only

**Routing**: Filter by `userTaskRun.userTaskDefId.name` matching a configured UserTaskDef name.

**Aggregation semantics**: Stateless — each `userTaskRun` record is a **full snapshot** of the UserTaskRun state at that point in time. The WFD transforms and forwards each record directly without accumulating state.

**Output format** (from metadata-analysis.md):

```json
{
  "id": "<userTaskRunId>",
  "wfRunId": "<wfRunId>",
  "userTaskDefName": "<name>",
  "userTaskDefVersion": "<version>",
  "status": "<UserTaskRunStatus>",
  "userId": "<userId or null>",
  "userGroup": "<userGroup or null>",
  "results": {
    "<field-name>": "<value>"
  },
  "createdAt": "<ISO-8601 timestamp>"
}
```

**Concrete example**: `it-request` userTaskRun from wfRunId `c59560f5`, first record (ASSIGNED):

```json
{
  "id": "c59560f521b34a158d58ac7b12c50eb0/0-1",
  "wfRunId": "c59560f521b34a158d58ac7b12c50eb0",
  "userTaskDefName": "it-request",
  "userTaskDefVersion": 0,
  "status": "ASSIGNED",
  "userId": "r2d2",
  "userGroup": "testGroup",
  "results": {},
  "createdAt": "2026-02-10T16:16:26.752Z"
}
```

See [Section 7](#7-usertaskdef-streamlet-worked-example) for the full lifecycle walk-through.

---

### 4.4 WorkflowEventDef Streamlets (stateless)

**Input records**: `workflowEvent` only

**Routing**: Filter by `workflowEvent.id.workflowEventDefId.name` matching a configured WorkflowEventDef name.

**Aggregation semantics**: Stateless — each `workflowEvent` record is transformed and forwarded directly.

**Output format** (from metadata-analysis.md):

```json
{
  "id": "<workflowEventId>",
  "wfRunId": "<wfRunId>",
  "workflowEventDefName": "<name>",
  "content": "<payload or null>",
  "createdAt": "<ISO-8601 timestamp>"
}
```

**Concrete example**: `sleep-done` from wfRunId `26ab000b`:

```json
{
  "id": "26ab000bba79445dbb85f4f5ceff7a30/sleep-done/0",
  "wfRunId": "26ab000bba79445dbb85f4f5ceff7a30",
  "workflowEventDefName": "sleep-done",
  "content": "hello there!",
  "createdAt": "2026-02-10T15:16:31.164Z"
}
```

The `id` is composed from `wfRunId/workflowEventDefId.name/number`. The `content` field extracts the value from the `VariableValue` oneof (`str: "hello there!"` → `"hello there!"`).

> **Note**: The `sleep-done` WorkflowEventDef has a void `contentType` in the metadata, yet the runtime record contains `{"str": "hello there!"}`. The WFD should handle this gracefully — accept any content regardless of the declared type (see metadata-analysis.md §6.9).

---

## 5. Key Observations for Implementation

1. **Kafka key = wfRunId for ALL record types** → This is the natural grouping key for Kafka Streams. All records for a given WfRun arrive in order within a partition, enabling stateful aggregation for WfSpec Streamlets.

2. **Variable records include `wfSpecId`** → Enables routing to the correct WfSpec Streamlet without requiring a metadata join. The `variable.wfSpecId.name` field directly tells us which WfSpec this variable belongs to.

3. **Only `PUBLIC_VAR` variables appear on the execution topic** → Confirmed by comparing our samples with the WfSpec metadata. The `example-variables` WfSpec has 5 variables (all `PRIVATE_VAR`) and emits zero variable records. The `output-topic` WfSpec has 2 `PUBLIC_VAR` variables and emits exactly those 2 as variable records. This is consistent with the Output Topic design (see `output-topic.md`, "Configuring What's Sent" section).

4. **UserTaskRun records are full snapshots** → Each record contains the complete state of the UserTaskRun (status, userId, userGroup, results, full events array). No aggregation needed — just transform and forward. The `epoch` field increments with each state change, providing a version number.

5. **WfRun records are full snapshots too** → But they need to be combined with Variable records for the `columns` field in WfSpec Streamlet output, requiring stateful aggregation.

6. **TaskRun records are NOT used by any Streamlet type** → TaskRun records are informational only for the WFD. They are present in the execution topic for observability but none of the four Streamlet types (WfSpec, ExternalEventDef, UserTaskDef, WorkflowEventDef) use them.

7. **CorrelatedEvent and Checkpoint** → Defined in the proto but not observed in samples and not used by any Streamlet type. The WFD should silently skip these record types.

8. **Multiple records can share the same timestamp** → Records processed within a single LH Command (e.g. `RunWf` triggers wfRun RUNNING + taskRun SCHEDULED + variable records all in one batch) share the same `timestamp`. The WFD processes them in the order they appear on the topic.

9. **UserTaskRun `source.userTaskTrigger` vs `source.taskNode`** → The `taskGuid` prefix distinguishes UT action-triggered tasks (`ut-` prefix, e.g. `ut-0-2-1`) from regular task node tasks (numeric prefix, e.g. `0-4`). The `source` field explicitly labels the origin. The WFD does not process taskRun records, but this is useful context.

10. **Output topic naming** → Follows the convention from the design doc: `{lhClusterId}_{tenant}_streamlet_{type-prefix}-{spec-name}`, with type prefixes:
    - `workflow-` for WfSpec Streamlets
    - `usertask-` for UserTaskDef Streamlets
    - `externalevent-` for ExternalEventDef Streamlets
    - `workflowevent-` for WorkflowEventDef Streamlets

11. **Variable value representation** → Variable values use the protobuf `VariableValue` oneof. An empty object `{}` represents null/unset. The WFD must extract the actual value from the appropriate oneof field (`str`, `int`, `double`, `bool`, `jsonObj`, `jsonArr`, `bytes`, `wfRunId`, `utcTimestamp`).

---

## 6. WfSpec Streamlet Worked Example

This section walks through wfRunId `2f5fa647` (`output-topic` WfSpec) step-by-step, showing how each incoming record updates the WfSpec Streamlet state and what gets emitted.

The `output-topic` WfSpec has two `PUBLIC_VAR` columns: `input-name` (STR) and `public-greeting` (STR).

### Record 1: wfRun RUNNING (T16:04:33.531Z)

**Input**: `wfRun` with status `RUNNING`, startTime `T16:04:33.531Z`

**State update**: Initialize state for this wfRunId.

**Emission**:
```json
{
  "id": "2f5fa6471cd346258a4da55ef45b93d3",
  "status": "RUNNING",
  "columns": {},
  "createdAt": "2026-02-10T16:04:33.531Z",
  "updatedAt": "2026-02-10T16:04:33.531Z"
}
```

No variable values yet — `columns` is empty.

### Record 2: taskRun SCHEDULED (T16:04:33.531Z)

**Input**: `taskRun` with taskDefId `greet`, status `TASK_SCHEDULED`

**Action**: **Skip** — taskRun records are not relevant for WfSpec Streamlets.

### Record 3: variable `input-name` = "Colt" (T16:04:33.531Z)

**Input**: `variable` with name `input-name`, value `{"str": "Colt"}`

**State update**: Set `columns["input-name"] = "Colt"`.

**Emission**:
```json
{
  "id": "2f5fa6471cd346258a4da55ef45b93d3",
  "status": "RUNNING",
  "columns": {
    "input-name": "Colt"
  },
  "createdAt": "2026-02-10T16:04:33.531Z",
  "updatedAt": "2026-02-10T16:04:33.531Z"
}
```

### Record 4: variable `public-greeting` = null (T16:04:33.531Z)

**Input**: `variable` with name `public-greeting`, value `{}` (null/unset)

**State update**: Set `columns["public-greeting"] = null`.

**Emission**:
```json
{
  "id": "2f5fa6471cd346258a4da55ef45b93d3",
  "status": "RUNNING",
  "columns": {
    "input-name": "Colt",
    "public-greeting": null
  },
  "createdAt": "2026-02-10T16:04:33.531Z",
  "updatedAt": "2026-02-10T16:04:33.531Z"
}
```

### Records 5-6: taskRun RUNNING and TIMEOUT

**Action**: **Skip** — taskRun records are not relevant for WfSpec Streamlets.

### Record 7: wfRun ERROR (T16:05:33.642Z)

**Input**: `wfRun` with status `ERROR`, endTime `T16:05:33.690Z`

**State update**: Update status to `ERROR`, update `updatedAt`.

**Emission**:
```json
{
  "id": "2f5fa6471cd346258a4da55ef45b93d3",
  "status": "ERROR",
  "columns": {
    "input-name": "Colt",
    "public-greeting": null
  },
  "createdAt": "2026-02-10T16:04:33.531Z",
  "updatedAt": "2026-02-10T16:05:33.642Z"
}
```

**Summary**: 7 input records → 4 Streamlet emissions (skipping 3 taskRun records). The `updatedAt` field reflects when the last state change occurred, while `createdAt` remains the WfRun start time.

---

## 7. UserTaskDef Streamlet Worked Example

This section walks through the `it-request` UserTaskDef Streamlet for wfRunId `c59560f5`, showing each userTaskRun record emitted as a full snapshot.

The `it-request` UserTaskDef has fields: `requestedItem` (STR, required) and `justification` (STR, required).

### Record 1: ASSIGNED (T16:16:26.748Z, epoch 1)

**Input**: `userTaskRun` with status `ASSIGNED`, userId: `r2d2`, userGroup: `testGroup`, results: `{}`

**Emission**:
```json
{
  "id": "c59560f521b34a158d58ac7b12c50eb0/0-1",
  "wfRunId": "c59560f521b34a158d58ac7b12c50eb0",
  "userTaskDefName": "it-request",
  "userTaskDefVersion": 0,
  "status": "ASSIGNED",
  "userId": "r2d2",
  "userGroup": "testGroup",
  "results": {},
  "createdAt": "2026-02-10T16:16:26.752Z"
}
```

The user task is initially assigned to `r2d2` in the `testGroup`.

### Record 2: UNASSIGNED (T16:17:26.752Z, epoch 2)

**Input**: `userTaskRun` with status `UNASSIGNED`, userGroup: `testGroup`, userId absent, results: `{}`

**Emission**:
```json
{
  "id": "c59560f521b34a158d58ac7b12c50eb0/0-1",
  "wfRunId": "c59560f521b34a158d58ac7b12c50eb0",
  "userTaskDefName": "it-request",
  "userTaskDefVersion": 0,
  "status": "UNASSIGNED",
  "userId": null,
  "userGroup": "testGroup",
  "results": {},
  "createdAt": "2026-02-10T16:16:26.752Z"
}
```

After ~60s, the assignment timed out. The `userId` was removed (assignment expired) but the task remains in the `testGroup` pool.

### Record 3: DONE (T16:17:29.669Z, epoch 3)

**Input**: `userTaskRun` with status `DONE`, userId: `r2d2`, userGroup: `testGroup`, results: `{requestedItem: {"str": "Test response"}, justification: {"str": "just because"}}`

**Emission**:
```json
{
  "id": "c59560f521b34a158d58ac7b12c50eb0/0-1",
  "wfRunId": "c59560f521b34a158d58ac7b12c50eb0",
  "userTaskDefName": "it-request",
  "userTaskDefVersion": 0,
  "status": "DONE",
  "userId": "r2d2",
  "userGroup": "testGroup",
  "results": {
    "requestedItem": "Test response",
    "justification": "just because"
  },
  "createdAt": "2026-02-10T16:16:26.752Z"
}
```

The user task was completed by `r2d2` with the request details. The `results` field now contains the user's form responses, with values extracted from the `VariableValue` oneof (`str` → plain string).

**Summary for `it-request` UserTaskDef**: 3 emissions tracking the lifecycle ASSIGNED → UNASSIGNED → DONE. Each emission is a complete snapshot — the downstream consumer sees the full current state without needing to reconstruct from deltas.

---

### `approve-it-request` UserTaskDef (same wfRunId `c59560f5`)

For completeness, here is the `approve-it-request` UserTaskDef lifecycle from the same WfRun:

| # | Timestamp | Status | userId | userGroup | results | epoch | Notes |
|---|-----------|--------|--------|-----------|---------|-------|-------|
| 1 | T16:17:29.669Z | UNASSIGNED | — | finance | {} | 1 | Assigned to finance group (no specific user) |
| 2 | T16:17:31.714Z | UNASSIGNED | — | finance | {} | 1 | Same status/epoch; `taskExecuted` event added (ON_ARRIVAL send-email triggered) |
| 3 | T16:19:03.087Z | ASSIGNED | mace | — | {} | 2 | Claimed by `mace` from finance group |
| 4 | T16:19:40.114Z | DONE | Lalalu | — | `{isApproved: true}` | 3 | Completed by `Lalalu` with approval |

**Observations**:
- Record #2 has the same status and epoch as #1, but the events array now includes a `taskExecuted` entry. The WFD should still emit this as a Streamlet record since the underlying state changed (the ON_ARRIVAL action was triggered).
- The `userGroup` field is present when the task is assigned to a group, and absent when assigned to a specific user (records #3, #4).
- The final userId (`Lalalu`) differs from the assigned userId (`mace`) — another user completed the task (perhaps via reassignment in the same command).
