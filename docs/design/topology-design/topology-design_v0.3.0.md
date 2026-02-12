# LittleHorse Workflow Diffuser — Kafka Streams Topology Design

**Version:** 0.3.0
**Date:** 2026-02-12
**Supersedes:** [v0.1.1](topology-design_v0.1.1.md)

## Overview

The Workflow Diffuser (`lh-wf-diffuser`) is a Kafka Streams application that consumes raw LittleHorse execution and metadata records and transforms them into queryable, tenant-scoped Streamlet output topics. It uses a two-sub-topology design:

- **Sub-topology 0 (Metadata Processor):** Ingests metadata records from compacted metadata topics, resolves tenants, and stores raw protobuf bytes in a local state store. The store's changelog feeds a `GlobalKTable` that provides every Diffuser Processor instance with a complete, local copy of all metadata.
- **Sub-topology 1 (Diffuser Processor):** Consumes execution records, dispatches by record type, enriches with metadata from the global store, and routes output to dynamic tenant-scoped sink topics via a `streamlet-topic` header and `TopicNameExtractor`.

Processing is **stateful** for `WfRun` and `Variable` records (aggregated into `wfrun-store`) and **stateless passthrough** for `UserTaskRun`, `ExternalEvent`, and `WorkflowEvent`. Dynamic topic routing is configuration-driven via `RecordRouter`, which resolves output topics based on configured tenants and spec names.

A **store eviction** mechanism for terminal WfRun entries is planned but not yet implemented.

## Input Topics

### Execution Topics

| Property    | Value                                                     |
| ----------- | --------------------------------------------------------- |
| Key         | `wfRunId` (string)                                        |
| Value       | `OutputTopicRecord` (protobuf)                            |
| Pattern     | `{lhClusterId}_(tenant1\|tenant2\|...)_execution`        |
| Naming      | `{lhClusterId}_{tenant}_execution`                       |
| Partitions  | `p > 1`                                                   |
| Retention   | `delete`                                                  |

### Metadata Topics

| Property    | Value                                                     |
| ----------- | --------------------------------------------------------- |
| Key         | `metaId` (string)                                         |
| Value       | `MetadataOutputTopicRecord` (protobuf)                    |
| Pattern     | `{lhClusterId}_(tenant1\|tenant2\|...)_metadata`         |
| Naming      | `{lhClusterId}_{tenant}_metadata`                        |
| Partitions  | `p = 1`                                                   |
| Retention   | `compact`                                                 |

Topic patterns are built by `InputTopicPatternBuilder`, which quotes the cluster ID (to escape special regex characters) and builds a tenant alternation from all configured tenant names.

## Output Topics

| Property    | Value                                                                        |
| ----------- | ---------------------------------------------------------------------------- |
| Key         | Entity ID (string)                                                           |
| Value       | `WfRun | UserTaskRun | ExternalEvent | WorkflowEvent` (JSON)                |
| Naming      | `{lhClusterId}_{tenant}_streamlet_{type-prefix}-{specName}`                 |
| Partitions  | `p > 1` (exact count TBD)                                                   |
| Replication | `r = 3`                                                                      |
| Retention   | `infinite`                                                                   |

Output is produced using a `TopicNameExtractor` (`StreamletTopicExtractor`) that reads the `streamlet-topic` header set by the `DiffuserProcessor`. Serialization uses Apicurio's JSON Schema serde with `TopicIdStrategy` (schema artifact = `{topic}-value`), with auto-registration enabled.

### Topic Name Examples

| Streamlet Type  | Spec Name            | Output Topic                                                   |
| --------------- | -------------------- | -------------------------------------------------------------- |
| WfRun           | order-processing     | `cluster1_tenant1_streamlet_workflow-order-processing`         |
| UserTaskRun     | confirm-purchase     | `cluster1_tenant1_streamlet_usertask-confirm-purchase`         |
| ExternalEvent   | payment-confirmed    | `cluster1_tenant1_streamlet_externalevent-payment-confirmed`   |
| WorkflowEvent   | order-shipped        | `cluster1_tenant1_streamlet_workflowevent-order-shipped`       |

### Output Schemas

**WfRun Streamlet:**

```json
{
  "id": "<wfRunId>",
  "status": "<LHStatus>",
  "columns": {
    "<var-name>": "<value>",
    "<var-name>": "<value>"
  },
  "createdAt": "<ISO-8601 UTC>",
  "updatedAt": "<ISO-8601 UTC>"
}
```

**UserTaskRun Streamlet:**

```json
{
  "id": "<wfRunId>/<userTaskGuid>",
  "wfRunId": "<wfRunId>",
  "userTaskDefName": "<name>",
  "userTaskDefVersion": "<version>",
  "status": "<UserTaskRunStatus>",
  "userId": "<userId or null>",
  "userGroup": "<userGroup or null>",
  "results": {
    "<field-name>": "<value>"
  },
  "createdAt": "<ISO-8601 UTC>"
}
```

**ExternalEvent Streamlet:**

```json
{
  "id": "<wfRunId>/<externalEventDefName>/<guid>",
  "wfRunId": "<wfRunId>",
  "content": "<payload>",
  "claimed": "<boolean>",
  "createdAt": "<ISO-8601 UTC>"
}
```

**WorkflowEvent Streamlet:**

```json
{
  "id": "<wfRunId>/<workflowEventDefName>/<number>",
  "wfRunId": "<wfRunId>",
  "workflowEventDefName": "<name>",
  "content": "<payload or null>",
  "createdAt": "<ISO-8601 UTC>"
}
```

## Topology

### Sub-topology 0 — Metadata Processor

Ingests metadata records, resolves tenants, and populates a global metadata store accessible to the diffuser processor in sub-topology 1.

```
metadata-source (KS Source, pattern: {lhClusterId}_(t1|t2|...)_metadata)
  → MetadataProcessor
    → global-metadata (Local State Store, KeyValueStore<String, byte[]>)
      → changelog topic ({applicationId}-global-metadata-changelog)
        → GlobalKTable: global-metadata-store (KeyValueStore<String, MetadataOutputTopicRecord>)
```

#### MetadataProcessor

- Resolves tenant from the input topic name via `TenantResolver`.
- Prefixes the record key with the tenant: `{tenant}/{metaId}`.
  - Example: `default/WF_SPEC/order-processing/00000/00000`
- Stores **raw protobuf bytes** in the `global-metadata` local store (lazy deserialization — bytes are only deserialized when read from the GlobalKTable).
- No downstream forwarding — records are stored only.

#### Changelog Topic

| Property    | Value                                              |
| ----------- | -------------------------------------------------- |
| Name        | `{applicationId}-global-metadata-changelog`        |
| Key         | `{tenant}/{metaId}` (string)                       |
| Value       | `MetadataOutputTopicRecord` (protobuf)             |
| Partitions  | `p = 1`                                            |
| Retention   | `compact`                                          |

### Sub-topology 1 — Diffuser Processor

The core processing topology. Consumes execution records, dispatches by record type, enriches with metadata from the global store, manages stateful WfRun aggregation, and routes output to dynamic tenant-scoped sink topics.

```
execution-source (KS Source, pattern: {lhClusterId}_(t1|t2|...)_execution)
  → DiffuserProcessor
    ├── reads: global-metadata-store (Global, read-only)
    ├── reads/writes: wfrun-store (Local State Store, KeyValueStore<String, byte[]>)
    ├── uses: RecordRouter (config-driven topic resolution)
    ├── uses: WfSpecAccessFilter (public variable filtering)
    → streamlet-sink (KS Sink, dynamic topic via StreamletTopicExtractor)
```

#### DiffuserProcessor

1. **Resolve tenant** from the input topic name via `TenantResolver`.
2. **Dispatch by record type** — the `OutputTopicRecord` payload `oneof` determines the processing path:

| Payload Type         | Processing Mode | Details                                                                                                                        |
| -------------------- | --------------- | ------------------------------------------------------------------------------------------------------------------------------ |
| `WfRun`              | Stateful        | Resolve topic via `RecordRouter` → Load state from `wfrun-store` → Apply via `WfRunTransformer.applyWfRun()` → Save → Forward |
| `Variable`           | Stateful        | Filter via `WfSpecAccessFilter` (PUBLIC_VAR only) → Load state from `wfrun-store` → Apply via `WfRunTransformer.applyVariable()` → Save → Forward |
| `UserTaskRun`        | Stateless       | Transform via `UserTaskRunTransformer` → Forward                                                                               |
| `ExternalEvent`      | Stateless       | Transform via `ExternalEventTransformer` → Forward                                                                             |
| `WorkflowEvent`      | Stateless       | Transform via `WorkflowEventTransformer` → Forward                                                                             |
| `TaskRun`            | Dropped         | Silently skipped (no streamlet defined)                                                                                        |
| `CorrelatedEvent`    | Dropped         | Silently skipped (no streamlet defined)                                                                                        |
| `TaskCheckpoint`     | Dropped         | Silently skipped (no streamlet defined)                                                                                        |

3. **Resolve dynamic sink topic** via `RecordRouter`, which checks the configured specs for the resolved tenant. If the spec is not configured, the record is silently dropped.
4. **Set `streamlet-topic` header** on the output record with the resolved topic name (UTF-8 encoded).
5. **Forward** the record downstream to the `StreamletTopicExtractor` sink.

#### WfRun Stateful Aggregation

WfRun and Variable records share the same `wfrun-store` entry, keyed by `{tenant}/{wfRunId}`. Each incoming record loads the existing state (if any), applies the update, saves back to the store, and forwards the latest snapshot:

- **WfRun update:** Sets `id`, `status`, `createdAt`, `updatedAt`; initializes empty `columns` if not present.
- **Variable update:** Adds/updates the variable in the `columns` object; preserves all other state. Only `PUBLIC_VAR` variables pass the `WfSpecAccessFilter`.

#### Public Variable Filtering

`WfSpecAccessFilter` looks up the `WfSpec` from the `global-metadata-store` using a key formatted as:

```
{tenant}/WF_SPEC/{name}/{%05d major}/{%05d revision}
```

It extracts public variable names from `frozenVariables` and all `ThreadSpec.variableDefs`, filtering for `WfRunVariableAccessLevel.PUBLIC_VAR`. Results are cached in an in-memory `HashMap<String, Set<String>>` within the `DiffuserProcessor`.

#### Dynamic Topic Routing

1. **`RecordRouter`** — Configuration-driven. Maintains four maps (`wfSpecsByTenant`, `userTaskDefsByTenant`, `externalEventDefsByTenant`, `workflowEventDefsByTenant`) built from `AppConfig`. Resolves record type + tenant + spec name to an `Optional<String>` topic name via `TopicNameResolver`.
2. **`TopicNameResolver`** — Formats the output topic name: `{lhClusterId}_{tenant}_streamlet_{type-prefix}-{specName}`.
3. **`StreamletTopicExtractor`** — `TopicNameExtractor` implementation that reads the `streamlet-topic` header from the `RecordContext`. Throws `IllegalStateException` if the header is missing.

#### State Store

| Store          | Key                    | Value                  | Scope | Persistence |
| -------------- | ---------------------- | ---------------------- | ----- | ----------- |
| `wfrun-store`  | `{tenant}/{wfRunId}`   | JSON bytes (ObjectNode)| Local | Persistent  |

### WfRun Store Eviction (Planned)

> **Status:** Planned — not yet implemented.

The `wfrun-store` accumulates state for every WfRun processed. Without eviction, the store grows unboundedly. The planned eviction mechanism:

- **Wall-clock punctuator** scheduled at a configurable interval.
- **Evicts terminal WfRun entries** — those with status `COMPLETED`, `HALTED`, or `ERROR` — that are older than a configurable retention period (based on `updatedAt`).
- **First implementation:** Silent removal from the store. No tombstone is emitted to the output topic.

## State Stores

| Store                    | Key                    | Value                            | Scope  | Persistence |
| ------------------------ | ---------------------- | -------------------------------- | ------ | ----------- |
| `global-metadata`        | `{tenant}/{metaId}`    | raw protobuf bytes               | Local (processor) | Persistent  |
| `global-metadata-store`  | `{tenant}/{metaId}`    | `MetadataOutputTopicRecord`      | Global (GKT) | Materialized from changelog |
| `wfrun-store`            | `{tenant}/{wfRunId}`   | JSON bytes (ObjectNode)          | Local  | Persistent  |

## Design Decisions

### GlobalKTable for Metadata Lookup

The metadata store is exposed as a `GlobalKTable` so that every instance of the Diffuser Processor (sub-topology 1) has a complete, local copy of all metadata. This avoids the need for cross-partition joins or repartitioning of execution records, and ensures metadata lookups are always local reads.

### Raw Bytes in Metadata Local Store

The `MetadataProcessor` stores raw protobuf bytes in its local `global-metadata` store rather than deserialized objects. Deserialization only happens when the `DiffuserProcessor` reads from the `GlobalKTable` (which uses a protobuf serde on the changelog topic). This avoids unnecessary deserialization in the metadata processor path.

### Dynamic Sink with StreamletTopicExtractor

Output records are routed to tenant- and streamlet-specific topics at runtime using a `TopicNameExtractor` (`StreamletTopicExtractor`). The target topic is conveyed via a `streamlet-topic` record header set by the `DiffuserProcessor`, decoupling topic resolution from the sink. Serialization uses Apicurio's JSON Schema serde with `TopicIdStrategy` and auto-registration.

### Configuration-Driven Record Routing

`RecordRouter` resolves output topics based on the configured tenants and their enabled specs (wfSpecs, userTaskDefs, externalEventDefs, workflowEventDefs). Records for unconfigured specs are silently dropped. This makes onboarding new tenants or specs a config-only change (rolling restart, no code changes).

### Stateless UserTaskRun

UserTaskRun records are processed statelessly — each record from the LH output topic is a complete snapshot of the user task state. No aggregation or local state store is needed, unlike WfRun which requires merging status updates with variable updates.

### Public Variable Filtering via Metadata Lookup

Only variables with `WfRunVariableAccessLevel.PUBLIC_VAR` are included in WfRun streamlet output. The `WfSpecAccessFilter` looks up the WfSpec from the global metadata store to determine which variables are public. Results are cached in-memory to avoid repeated metadata store lookups for the same WfSpec version.

### Tenant Resolution from Topic Name

Both processors resolve the tenant identifier from the input topic name (via `TenantResolver`) rather than from the record payload. This keeps tenant routing independent of the protobuf schema and consistent across execution and metadata paths.

## Open Items

- [ ] Tombstone emission on eviction — emit tombstone to output topic or silent store removal only?
- [ ] Eviction retention period default value
- [ ] Partition count strategy for output topics (`p > 1` — exact count TBD)
