> **Superseded** — This document has been superseded by [topology-design_v0.3.0.md](topology-design_v0.3.0.md).

# LittleHorse Workflow Diffuser — Kafka Streams Topology Design

**Version:** 0.1.0
**Date:** 2026-02-09

## Overview

The Workflow Diffuser (`lh-wf-diffuser`) is a Kafka Streams application that consumes raw LittleHorse execution and metadata records and transforms them into queryable, tenant-scoped Streamlet output topics. It uses a two-sub-topology design: one for metadata ingestion via a GlobalKTable, and one for the core diffusion logic that enriches and routes execution records to dynamic sink topics.

## Input Topics

### Execution Topics

| Property    | Value                                              |
| ----------- | -------------------------------------------------- |
| Key         | `wfRunId` (string)                                 |
| Value       | `OutputTopicRecord` (protobuf)                     |
| Pattern     | `cluster1_[\\\\]_executionS/`                      |
| Naming      | `{LHS_CLUSTER_ID}_{tenant}_execution`              |
| Partitions  | `p > 1`                                            |
| Retention   | `infinite`                                         |

### Metadata Topics

| Property    | Value                                              |
| ----------- | -------------------------------------------------- |
| Key         | `metaId` (string)                                  |
| Value       | `MetadataOutputTopicRecord` (protobuf)             |
| Pattern     | `cluster1_[\\\\]_metadataS/`                       |
| Naming      | `{LHS_CLUSTER_ID}_{tenant}_metadata`               |
| Partitions  | `p = 1`                                            |
| Retention   | `compact`                                          |

## Output Topics

| Property    | Value                                                        |
| ----------- | ------------------------------------------------------------ |
| Key         | `metricId` (string)                                          |
| Value       | `WfRun \| UserTaskRun \| ExternalEvent \| WorkflowEvent` (string/JSON, ~250N) |
| Naming      | `{lhClusterId}_{tenant}_streamlet_{streamlet-name}`          |
| Partitions  | `p > 1`                                                      |
| Replication | `r = 3`                                                      |
| Retention   | `infinite`                                                   |

Output is produced using a `TopicNameExtractor` with Apicurio's `TopicIdStrategy`. Schemas must already be registered in the Schema Registry under the respective topic names.

```java
stream.to(
    (key, value, ctx) -> deriveTopicFrom(ctx),
    Produced.with(keySerde, apicurioSchemaSerde)
);
```

## Topology

### Sub-topology 0 — Metadata Processor

Responsible for ingesting metadata records, resolving tenants, and populating a global metadata store accessible to the diffuser processor in sub-topology 1.

```
metadata-source (KS Source)
  → MetadataProcessor
    → tenant-metadata-store (State Store, changelog-backed)
      → GlobalKTable (GKT)
        → global-metadata-store
```

#### MetadataProcessor

- Resolves tenant from the input topic name.
- Prefixes the record key with the tenant: `{tenant}/{metaId}`.
- Persists to the `tenant-metadata-store`.
- The state store produces to its changelog topic automatically.

#### State Stores

| Store                    | Key                  | Value                        | Scope  |
| ------------------------ | -------------------- | ---------------------------- | ------ |
| `tenant-metadata-store`  | `{tenant}/{metaId}`  | `MetadataOutputTopicRecord`  | Local  |
| `global-metadata-store`  | `{tenant}/{metaId}`  | `MetadataOutputTopicRecord`  | Global |

#### Changelog Topic

| Property    | Value                                          |
| ----------- | ---------------------------------------------- |
| Name        | `cluster1-wf-diffuser-metadata-changelog`      |
| Key         | `{tenant}/{metaId}` (string)                   |
| Value       | `MetadataOutputTopicRecord` (protobuf)         |
| Partitions  | `p = 1`                                        |
| Retention   | `compact`                                      |

### Sub-topology 1 — Diffuser Processor

The core processing topology. Consumes execution records, enriches them with metadata from the global store, manages stateful entities, and routes output to dynamic tenant-scoped sink topics.

```
execution-source (KS Source)
  → DiffuserProcessor
    ├── reads: global-metadata-store (Global, read-only)
    ├── reads/writes: user-task-store (Local State Store)
    ├── reads/writes: wfrun-store (Local State Store)
    → streamlet-sink (KS Sink, dynamic topic via TopicNameExtractor)
```

#### DiffuserProcessor

1. **Resolve tenant** from the input topic name.
2. **Transform** execution `OutputTopicRecord` to Streamlet value types (typed as `JsonNode`).
3. **Resolve dynamic sink topic**, adding a `streamlet-topic` record header.
4. **Route by record type:**

| Record Type       | Processing Mode | Details                                                              |
| ----------------- | --------------- | -------------------------------------------------------------------- |
| `ExternalEvent`   | Stateless       | Passthrough — transform and forward directly.                        |
| `WorkflowEvent`   | Stateless       | Passthrough — transform and forward directly.                        |
| `WfRun`           | Stateful        | Lookup `WfSpecDef` from `global-metadata-store`. Create/patch entity, upsert to `wfrun-store`. Emit latest. |
| `UserTaskRun`     | Stateful        | Lookup `UserTaskDef` from `global-metadata-store`. Create/patch entity, upsert to `user-task-store`. Emit latest. |

#### State Stores

| Store              | Key     | Value   | Notes                            |
| ------------------ | ------- | ------- | -------------------------------- |
| `user-task-store`  | `#TBC#` | `#TBC#` | Stateful UserTaskRun entities    |
| `wfrun-store`      | `#TBC#` | `#TBC#` | Stateful WfRun entities          |

> **Note:** Key/value types for `user-task-store` and `wfrun-store` are TBC (to be confirmed).

## Design Decisions

### GlobalKTable for Metadata Lookup

The metadata store is exposed as a `GlobalKTable` so that every instance of the Diffuser Processor (sub-topology 1) has a complete, local copy of all metadata. This avoids the need for cross-partition joins or repartitioning of execution records, and ensures metadata lookups are always local reads.

### Dynamic Sink with TopicNameExtractor

Output records are routed to tenant- and streamlet-specific topics at runtime using `TopicNameExtractor`. The target topic is derived from the processing context (`deriveTopicFrom(ctx)`), enabling flexible multi-tenant output without hardcoded topic names. Serialization uses Apicurio's schema-aware serde (`apicurioSchemaSerde`).

### Schema Registry Requirement

All output topic schemas must be pre-registered in the Apicurio Schema Registry under the respective topic names before the diffuser produces to them. This is enforced by Apicurio's `TopicIdStrategy`.

### Tenant Resolution

Both processors resolve the tenant identifier from the input topic name rather than from the record payload. This keeps tenant routing independent of the protobuf schema and consistent across execution and metadata paths.

## Open Items

- [ ] Finalize key/value types for `user-task-store` and `wfrun-store`.
- [ ] Define the exact `deriveTopicFrom(ctx)` logic and what context fields are used.
- [ ] Clarify the `#TBC#` placeholders for stateful store schemas.
- [ ] Determine partition count strategy for output topics (`p > 1` — exact count TBD).
- [ ] Define error handling / dead-letter strategy for transformation failures.
- [ ] Specify exactly which fields from `WfSpecDef` / `UserTaskDef` are used for enrichment.
