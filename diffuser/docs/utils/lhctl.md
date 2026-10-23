# lhctl — LH Command-Line Interface

`lhctl` is the CLI for interacting with a LH Kernel cluster. It wraps the gRPC API and supports managing metadata, running workflows, posting events, searching resources, and more.

**Installed version:** 0.16.0

## Configuration

```
--configFile <path>   # default: ~/.config/littlehorse.config
```

The config file contains connection details (host, port, TLS certs, OAuth tokens, tenant ID).

## Command Reference

### Identity & Auth

| Command | Description |
|---|---|
| `lhctl whoami` | Print the current logged-in Principal |
| `lhctl login` | OAuth2 login flow |
| `lhctl version` | Print client and server version |

### Running Workflows

```bash
# Run a WfSpec with input variables (name/value pairs)
lhctl run <wfSpecName> [<varName> <varValue>]...
  --wfRunId <id>           # set explicit ID (idempotence)
  --majorVersion <n>       # pin to specific major version
  --revision <n>           # pin to specific revision
  --parentWfRunId <id>     # set parent for child workflows

# Schedule recurring runs with a cron expression
lhctl schedule run <cronExpression> <wfSpecName> [<varName> <varValue>]...
  --id <id>
  --majorVersion <n>
  --revision <n>
```

Variable values are auto-deserialized to their appropriate types. JSON objects are passed as strings:
```bash
lhctl run my-workflow foo '{"bar":"baz"}'
```

### Workflow Lifecycle

```bash
lhctl stop wfRun <wfRunId>       # Stop a running WfRun
lhctl resume wfRun <wfRunId>     # Resume a stopped WfRun
lhctl rescue <wfRunId> <threadRunNumber>
  --skipCurrentNode              # skip failed node (default: retry it)
```

`rescue` restarts a ThreadRun in ERROR state — either retries the failed Node or skips to the next.

### Posting External Events

```bash
lhctl postEvent <wfRunId> <externalEventName> [<varType> <payload>]
  --guid <guid>              # for idempotence
  --threadRunNumber <n>      # target specific thread
  --nodeRunNumber <n>        # target specific node
```

Omit the last two args to post an event with no payload.

### User Tasks

```bash
# Assign/reassign a UserTaskRun
lhctl assign userTaskRun <wfRunId> <userTaskGuid>
  --userId <id>
  --userGroup <group>
  --overrideClaim            # force reassign if already claimed

# Interactively complete a UserTaskRun (prompts for each field)
lhctl execute userTaskRun <wfRunId> <userTaskGuid>

# Save progress without completing
lhctl save userTaskRun ...

# Cancel a UserTaskRun
lhctl cancel userTaskRun <wfRunId> <userTaskGuid>
```

### Getting Resources

`lhctl get <resourceType>` fetches a single resource by its identifier(s).

| Resource | Usage |
|---|---|
| `wfSpec` | `lhctl get wfSpec <name> [--majorVersion N] [--revision N]` |
| `wfRun` | `lhctl get wfRun <id>` |
| `taskDef` | `lhctl get taskDef <name>` |
| `taskRun` | `lhctl get taskRun <wfRunId> <taskRunGuid>` |
| `variable` | `lhctl get variable <wfRunId> <threadRunNumber> <varName>` |
| `nodeRun` | `lhctl get nodeRun <wfRunId> <threadRunNumber> <nodeRunPosition>` |
| `externalEvent` | `lhctl get externalEvent <wfRunId> <externalEventDefName> <guid>` |
| `externalEventDef` | `lhctl get externalEventDef <name>` |
| `workflowEvent` | `lhctl get workflowEvent <wfRunId> <workflowEventDefName> <number>` |
| `workflowEventDef` | `lhctl get workflowEventDef <name>` |
| `userTaskDef` | `lhctl get userTaskDef <name> [--version N]` |
| `userTaskRun` | `lhctl get userTaskRun <wfRunId> <threadRunNumber> <userTaskRunPosition>` |
| `structDef` | `lhctl get structDef <name>` |
| `correlatedEvent` | `lhctl get correlatedEvent ...` |
| `principal` | `lhctl get principal <id>` |
| `tenant` | `lhctl get tenant <id>` |
| `taskWorkerGroup` | `lhctl get taskWorkerGroup <id>` |
| `scheduled` | `lhctl get scheduled <id>` |

Composite IDs can be passed as separate args or `/`-delimited (e.g. `<wfRunId>/<threadRun>/<varName>`).

### Listing Resources (by WfRun)

`lhctl list <resourceType> <wfRunId>` lists all instances of a resource within a specific WfRun.

| Resource | Usage |
|---|---|
| `variable` | `lhctl list variable <wfRunId>` |
| `nodeRun` | `lhctl list nodeRun <wfRunId>` |
| `taskRun` | `lhctl list taskRun <wfRunId>` |
| `userTaskRun` | `lhctl list userTaskRun <wfRunId>` |
| `externalEvent` | `lhctl list externalEvent <wfRunId>` |
| `workflowEvent` | `lhctl list workflowEvent <wfRunId>` |

Pagination flags: `--limit <n>` (default 100), `--bookmark <base64>`.

### Searching Resources

`lhctl search <resourceType>` finds resources by criteria. Returns IDs usable with `lhctl get`.

**WfRun search:**
```bash
lhctl search wfRun <wfSpecName> [<majorVersion>] [<revision>]
  --status <RUNNING|COMPLETED|HALTING|HALTED|ERROR|EXCEPTION>
  --earliestMinutesAgo <n>
  --latestMinutesAgo <n>

# Search child workflows
lhctl search wfRun byParent <parentWfRunId>
```

**Variable search (by value):**
```bash
lhctl search variable
  --wfSpecName <name>
  --name <varName>
  --varType <STR|INT|DOUBLE|BOOL|JSON_OBJ|JSON_ARR>
  --value <value>
  --wfSpecMajorVersion <n>
  --wfSpecRevision <n>
```

**TaskRun search:**
```bash
lhctl search taskRun [<taskDefName>]
  --status <TASK_SCHEDULED|TASK_RUNNING|TASK_SUCCESS|TASK_FAILED|TASK_TIMEOUT|TASK_OUTPUT_SERDE_ERROR|TASK_INPUT_VAR_SUB_ERROR>
  --earliestMinutesAgo <n>
  --latestMinutesAgo <n>
```

**NodeRun search:**
```bash
lhctl search nodeRun <nodeType> <status>
  --earliestMinutesAgo <n>
  --latestMinutesAgo <n>
# nodeType: TASK, EXTERNAL_EVENT, ENTRYPOINT, EXIT, START_THREAD,
#           WAIT_THREADS, SLEEP, USER_TASK, START_MULTIPLE_THREADS
# status:   STARTING, RUNNING, COMPLETED, HALTING, HALTED, ERROR, EXCEPTION
```

**UserTaskRun search:**
```bash
lhctl search userTaskRun
  --userTaskDefName <name>
  --userTaskStatus <status>
  --userId <id>           # mutually exclusive with --userGroup
  --userGroup <group>
  --earliestMinutesAgo <n>
  --latestMinutesAgo <n>
```

**Metadata search** (no extra flags, returns all):
```bash
lhctl search wfSpec
lhctl search taskDef
lhctl search externalEventDef
lhctl search workflowEventDef
lhctl search userTaskDef
lhctl search structDef
lhctl search principal
lhctl search tenant
```

Pagination flags: `--limit <n>` (default 100), `--bookmark <base64>`.

### Deploying / Creating Resources

```bash
# Deploy from a JSON or Protobuf file
lhctl deploy wfSpec <file>           [--proto]
lhctl deploy taskDef <file>          [--proto]
lhctl deploy externalEventDef <file> [--proto]
lhctl deploy userTaskDef <file>      [--proto]
lhctl deploy principal <file>        [--proto]

# Create resources directly
lhctl put tenant <tenantId>
lhctl put principal ...
lhctl put correlatedEvent ...
lhctl put workflowEventDef <name>
```

### Deleting Resources

```bash
lhctl delete wfRun <id>
lhctl delete wfSpec <name> <majorVersion> <revision>
lhctl delete taskDef <name>
lhctl delete externalEventDef <name>
lhctl delete workflowEventDef <name>
lhctl delete userTaskDef <name>
lhctl delete structDef <name>
lhctl delete principal <id>
lhctl delete correlatedEvent ...
lhctl delete schedule <id>
```

### Metrics

```bash
lhctl wfSpecMetrics <wfSpecName> <wfSpecVersion> <windowType> <numWindows>
# windowType: MINUTES_5, HOURS_2, DAYS_1
# Example: last hour in 5-min windows
lhctl wfSpecMetrics my-workflow 0 MINUTES_5 12

# Cluster-level metrics
lhctl wfSpecMetrics CLUSTER_LEVEL_METRIC 0 HOURS_2 24
```

## Status Enums Reference

**WfRun statuses:** `RUNNING`, `COMPLETED`, `HALTING`, `HALTED`, `ERROR`, `EXCEPTION`

**TaskRun statuses:** `TASK_SCHEDULED`, `TASK_RUNNING`, `TASK_SUCCESS`, `TASK_FAILED`, `TASK_TIMEOUT`, `TASK_OUTPUT_SERDE_ERROR`, `TASK_INPUT_VAR_SUB_ERROR`

**NodeRun statuses:** `STARTING`, `RUNNING`, `COMPLETED`, `HALTING`, `HALTED`, `ERROR`, `EXCEPTION`

**Node types:** `TASK`, `EXTERNAL_EVENT`, `ENTRYPOINT`, `EXIT`, `START_THREAD`, `WAIT_THREADS`, `SLEEP`, `USER_TASK`, `START_MULTIPLE_THREADS`

**UserTaskRun statuses:** `UNASSIGNED`, `ASSIGNED`, `DONE`, `CANCELLED`

**Variable types:** `STR`, `INT`, `DOUBLE`, `BOOL`, `JSON_OBJ`, `JSON_ARR`
