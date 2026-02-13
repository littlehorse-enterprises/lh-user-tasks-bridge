# WfRun Store Eviction

## Overview

The `wfrun-store` in the `DiffuserProcessor` accumulates state for every WfRun processed, keyed by `{tenant}/{wfRunId}` with the value being a JSON `ObjectNode` serialized as `byte[]`. Without eviction, the store grows unboundedly as new workflow runs are created over time.

This design introduces a **wall-clock punctuator** and a **secondary state store** (`wfrun-eviction-store`) that together perform periodic eviction sweeps, removing entries for WfRuns that have been in a terminal status for longer than a configurable retention period.

Key properties:

- **Event-time based:** Eviction timestamps are derived from the WfRun's `updatedAt` field (the time the WfRun entered a terminal status), not from wall-clock time at processing. This ensures deterministic behavior regardless of processing lag.
- **Cancellable:** If a WfRun leaves a terminal status (e.g., `ERROR` → `RUNNING` via rescue), its pending eviction is cancelled.
- **Silent removal:** Evicted entries are deleted from the store without emitting tombstones to the output topic.

## Configuration

| Property | Type | Default | Description |
|---|---|---|---|
| `app.eviction.sweep-interval-ms` | `long` | `3600000` (1 hour) | Wall-clock interval between punctuator eviction sweeps |
| `app.eviction.fallback-retention-seconds` | `long` | `2592000` (30 days) | Retention period used when the WfSpec has no `WorkflowRetentionPolicy` |

Configuration is added to `AppConfig`:

```java
interface EvictionConfig {
    @WithDefault("3600000")
    long sweepIntervalMs();

    @WithDefault("2592000")
    long fallbackRetentionSeconds();
}
```

## Eviction-Eligible Statuses

A WfRun is eligible for eviction when its `status` is one of the following terminal statuses from `LHStatus`:

| Status | Proto Value | Description |
|---|---|---|
| `COMPLETED` | `2` | Workflow completed successfully |
| `ERROR` | `5` | Technical failure (task timeout, type error, worker crash) |
| `EXCEPTION` | `6` | Business exception defined by the user |

Additionally, `UNRECOGNIZED` (proto unknown value) is treated as eviction-eligible since it represents an unmappable status that cannot be acted upon.

### Why HALTED Is Not Eviction-Eligible

`HALTED` (proto value `4`) is **not** a terminal status. It represents a paused workflow that can be resumed. Evicting a `HALTED` WfRun would lose state for a workflow that may continue processing. Similarly, `HALTING` (proto value `3`) is a transitional state moving toward `HALTED` and is also not eligible.

## Retention Resolution

The retention period determines how long after termination a WfRun entry is kept before eviction. It is resolved as follows:

1. **Look up the WfSpec** from the `global-metadata-store` using the key format `{tenant}/WF_SPEC/{name}/{%05d major}/{%05d revision}` (same lookup used by `WfSpecAccessFilter`).
2. **Check for `WorkflowRetentionPolicy`:** If `retention_policy` is present with `seconds_after_wf_termination`, use that value.
3. **Fallback:** If the WfSpec has no retention policy, or if the WfSpec is not found in the store, use `app.eviction.fallback-retention-seconds`.

```
retentionSeconds = wfSpec.retentionPolicy.secondsAfterWfTermination
                   ?? appConfig.eviction.fallbackRetentionSeconds
```

## Eviction Store

A secondary state store tracks scheduled evictions, enabling efficient range scans instead of full iteration over the `wfrun-store`.

### Store Specification

| Property | Value |
|---|---|
| Store name | `wfrun-eviction-store` |
| Type | `KeyValueStore<String, byte[]>` |
| Persistence | Persistent |
| Key | `{epoch-millis-zero-padded-16}/{tenant}/{wfRunId}` |
| Value | UTF-8 bytes of the `wfrun-store` key (`{tenant}/{wfRunId}`) |

### Key Format

The eviction store key embeds the scheduled eviction timestamp as a zero-padded 16-digit epoch millisecond prefix:

```
{epoch-millis-zero-padded-16}/{tenant}/{wfRunId}
```

Examples:

```
0001739212800000/tenant1/abc123def456     # evictAt = 2025-02-10T16:00:00Z
0001741804800000/tenant1/abc123def456     # evictAt = 2025-03-12T16:00:00Z
```

Zero-padding to 16 digits ensures lexicographic ordering equals chronological ordering. A range scan from the store's first key to `{now-zero-padded-16}0` (with trailing `0` to include all entries at the exact `now` timestamp) efficiently finds all entries due for eviction.

### Eviction Timestamp Calculation

The eviction timestamp is computed from the WfRun's `updatedAt` field, not from the wall clock at processing time:

```
evictAtMillis = parseIso8601(state.get("updatedAt")) + (retentionSeconds * 1000)
evictionKey = String.format("%016d/%s", evictAtMillis, wfrunStoreKey)
```

Using `updatedAt` ensures that the eviction time is deterministic and reflects when the WfRun actually terminated, not when the record happened to be processed. This is important for correctness under consumer lag or replay scenarios.

## Processing Logic

### Changes to `processWfRun()`

After applying the WfRun update and saving state, the processor checks the new status and manages eviction scheduling:

```java
private void processWfRun(String wfRunId, String tenant, OutputTopicRecord otr, long timestamp) {
    WfRun wfRun = otr.getWfRun();
    String wfSpecName = wfRun.getWfSpecId().getName();

    Optional<String> targetTopic = recordRouter.resolveWfSpecTopic(tenant, wfSpecName);
    if (targetTopic.isEmpty()) return;

    String storeKey = tenant + "/" + wfRunId;
    ObjectNode state = loadWfRunState(storeKey);
    state = WfRunTransformer.applyWfRun(wfRun, state, otr.getTimestamp());

    // --- Eviction scheduling ---
    if (isEvictionEligible(wfRun.getStatus())) {
        scheduleEviction(storeKey, state, wfRun.getWfSpecId());
    } else {
        cancelEviction(storeKey, state);
    }

    saveWfRunState(storeKey, state);
    forward(wfRunId, stripInternalFields(state), targetTopic.get(), timestamp);
}
```

### `isEvictionEligible()`

```java
private static boolean isEvictionEligible(LHStatus status) {
    return status == LHStatus.COMPLETED
        || status == LHStatus.ERROR
        || status == LHStatus.EXCEPTION
        || status == LHStatus.UNRECOGNIZED;
}
```

### `scheduleEviction()`

1. If the state already has an `_evictionKey`, cancel the previous eviction first (the retention period or `updatedAt` may have changed).
2. Resolve the retention period from the WfSpec's `WorkflowRetentionPolicy` or fall back to config.
3. Compute `evictAtMillis` from `updatedAt + retentionSeconds * 1000`.
4. Build the eviction key: `String.format("%016d/%s", evictAtMillis, storeKey)`.
5. Write to `wfrun-eviction-store`: key = eviction key, value = `storeKey` as UTF-8 bytes.
6. Set `_evictionKey` on the state `ObjectNode` (internal bookkeeping).

```java
private void scheduleEviction(String storeKey, ObjectNode state, WfSpecId wfSpecId) {
    // Cancel existing eviction if re-terminating
    cancelEviction(storeKey, state);

    long retentionSeconds = resolveRetentionSeconds(storeKey, wfSpecId);
    long updatedAtMillis = Instant.parse(state.get("updatedAt").asText()).toEpochMilli();
    long evictAtMillis = updatedAtMillis + (retentionSeconds * 1000);
    String evictionKey = String.format("%016d/%s", evictAtMillis, storeKey);

    evictionStore.put(evictionKey, storeKey.getBytes(StandardCharsets.UTF_8));
    state.put("_evictionKey", evictionKey);
}
```

### `cancelEviction()`

If the state has an `_evictionKey`, delete the entry from the eviction store and remove the field from state:

```java
private void cancelEviction(String storeKey, ObjectNode state) {
    if (state != null && state.has("_evictionKey")) {
        String evictionKey = state.get("_evictionKey").asText();
        evictionStore.delete(evictionKey);
        state.remove("_evictionKey");
    }
}
```

### `_evictionKey` Lifecycle

The `_evictionKey` field is an internal bookkeeping field stored in the `wfrun-store` state. It tracks the currently active eviction store key so that cancellation and re-scheduling can efficiently locate and remove the eviction entry.

**This field must be stripped before forwarding to the output topic.** A helper method removes internal fields:

```java
private static ObjectNode stripInternalFields(ObjectNode state) {
    ObjectNode output = state.deepCopy();
    output.remove("_evictionKey");
    return output;
}
```

### Variable Processing

Variable records (`processVariable()`) do **not** interact with eviction scheduling. A variable update changes the `columns` and `updatedAt` of the state but does not affect the WfRun's status. The eviction schedule is determined solely by WfRun status transitions.

The `forward()` call in `processVariable()` must also strip internal fields before forwarding.

## Punctuator Logic

A wall-clock punctuator is registered in `DiffuserProcessor.init()` to perform periodic eviction sweeps:

```java
@Override
public void init(ProcessorContext<String, JsonNode> context) {
    this.context = context;
    this.wfRunStore = context.getStateStore(WFRUN_STORE);
    this.evictionStore = context.getStateStore(WFRUN_EVICTION_STORE);
    this.globalMetadataStore = context.getStateStore(GLOBAL_METADATA_STORE);

    context.schedule(
        Duration.ofMillis(evictionSweepIntervalMs),
        PunctuationType.WALL_CLOCK_TIME,
        this::evictExpiredEntries
    );
}
```

### Sweep Algorithm

```java
private void evictExpiredEntries(long timestamp) {
    String upperBound = String.format("%016d0", timestamp);

    try (var iterator = evictionStore.range(null, upperBound)) {
        while (iterator.hasNext()) {
            KeyValue<String, byte[]> entry = iterator.next();
            String wfrunStoreKey = new String(entry.value, StandardCharsets.UTF_8);

            wfRunStore.delete(wfrunStoreKey);
            evictionStore.delete(entry.key);

            log.debug("Evicted wfrun-store entry: key={}, evictionKey={}", wfrunStoreKey, entry.key);
        }
    }
}
```

Steps:

1. Compute the upper bound key: `{now-zero-padded-16}0`. The trailing `0` character is lexicographically greater than `/`, ensuring all entries with timestamps `<= now` are included.
2. Range scan from the beginning of the eviction store to the upper bound.
3. For each entry:
   - Delete the WfRun state from `wfrun-store` using the value (the wfrun-store key).
   - Delete the eviction entry from `wfrun-eviction-store`.
4. No output is emitted — eviction is silent store cleanup.

## State Store Updates

The eviction mechanism adds one new state store. Updated state store table:

| Store | Key | Value | Scope | Persistence |
|---|---|---|---|---|
| `global-metadata` | `{tenant}/{metaId}` | raw protobuf bytes | Local (processor) | Persistent |
| `global-metadata-store` | `{tenant}/{metaId}` | `MetadataOutputTopicRecord` | Global (GKT) | Materialized from changelog |
| `wfrun-store` | `{tenant}/{wfRunId}` | JSON bytes (ObjectNode) | Local | Persistent |
| `wfrun-eviction-store` | `{epoch-millis-016d}/{tenant}/{wfRunId}` | UTF-8 bytes of wfrun-store key | Local | Persistent |

The new store is registered in `TopologyFactory` alongside `wfrun-store`:

```java
builder.addStateStore(Stores.keyValueStoreBuilder(
    Stores.persistentKeyValueStore(DiffuserProcessor.WFRUN_EVICTION_STORE),
    Serdes.String(), Serdes.ByteArray()));
```

And connected to the `DiffuserProcessor`:

```java
builder.stream(patternBuilder.executionTopicPattern(), ...)
    .process(() -> new DiffuserProcessor(tenantResolver, recordRouter, appConfig.eviction()),
        Named.as("diffuser-processor"),
        DiffuserProcessor.WFRUN_STORE,
        DiffuserProcessor.WFRUN_EVICTION_STORE);
```

## Edge Cases

### Re-termination

A WfRun may transition through multiple terminal statuses (e.g., `COMPLETED`, then replayed as `ERROR`). Each WfRun status update cancels the previous eviction (if any) before scheduling a new one. The `_evictionKey` field ensures the old eviction entry is located and removed, preventing ghost evictions.

### Records After Eviction

After a WfRun is evicted, new Variable records for the same `wfRunId` may still arrive (Kafka ordering is per-partition, and variable records may lag). The processor handles this naturally: `loadWfRunState()` returns `null`, and the variable is applied to a fresh skeleton state. This is acceptable because:

- The WfRun was terminal for the full retention period, so further variable updates are unlikely.
- If they do arrive, the partial state will itself be evicted after the next terminal status update (or cleaned up by a future enhancement).

### Partition Rebalancing

On rebalance, Kafka Streams restores both `wfrun-store` and `wfrun-eviction-store` from their respective changelogs. Since both stores are persistent and backed by changelogs, eviction state survives rebalancing. The punctuator is re-registered in `init()` on the new processor instance.

### Out-of-Order Status Updates

If a non-terminal status update arrives after a terminal one (e.g., due to reprocessing), `cancelEviction()` removes the pending eviction. The WfRun remains in the store until a new terminal status triggers re-scheduling. This is the desired behavior — eviction should only happen for WfRuns that are durably in a terminal state.

## Key Design Decisions

### Secondary Eviction Store vs. Full Store Iteration

A naive approach would iterate all entries in `wfrun-store` on each sweep, checking timestamps. This has O(n) cost per sweep over the entire store. The secondary `wfrun-eviction-store` with timestamp-prefixed keys enables O(k) range scans where k is the number of due entries, regardless of total store size.

### `_evictionKey` for Cancellation

Tracking the eviction key inside the wfrun-store state allows O(1) cancellation without scanning the eviction store. When a WfRun leaves a terminal status, the processor reads the `_evictionKey` from state and deletes the eviction entry directly. The alternative (scanning the eviction store by suffix) would require a full iteration.

### `updatedAt`-Based Eviction Timing

Using the WfRun's `updatedAt` timestamp (from the record) rather than the wall clock at processing time ensures:

- **Deterministic behavior:** Replaying records produces the same eviction schedule.
- **Correctness under lag:** A consumer that falls behind does not prematurely evict entries that would be kept under normal processing.
- **Alignment with LH semantics:** The `WorkflowRetentionPolicy.seconds_after_wf_termination` is defined relative to termination time, which corresponds to `updatedAt`.

### Silent Removal (No Tombstone)

The first implementation removes entries silently from the store without producing tombstones to the output topic. This simplifies the design and avoids downstream consumers needing to handle delete markers. Tombstone emission is deferred to future work.

## Future Work

- **Tombstone emission:** Optionally emit a tombstone (null value) to the output topic on eviction, allowing downstream consumers to detect deletions.
- **Batching / cursor optimization:** If sweep volume becomes large, process evictions in batches with a cursor to avoid long punctuator runs that block record processing.
- **Metrics:** Expose eviction counters (evicted count, sweep duration, eviction store size) via Micrometer for operational monitoring.
- **(Idea) Per-tenant retention overrides:** Allow retention configuration at the tenant level in addition to WfSpec-level and global fallback.
