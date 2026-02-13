# Workflow Diffuser - Design Document

- [Overview](#overview)
- [Tech Stack](#tech-stack)
- [Dependencies](#dependencies)
- [Application Architecture](#application-architecture)
  - [Package Structure](#package-structure)
  - [Application Bootstrap](#application-bootstrap)
  - [HTTP Server](#http-server)
  - [Health Checks](#health-checks)
  - [Metrics](#metrics)
- [Configuration](#configuration)
  - [SmallRye Config Setup](#smallrye-config-setup)
  - [Configuration Precedence](#configuration-precedence)
  - [Environment Variable Mapping](#environment-variable-mapping)
  - [External Config Files](#external-config-files)
  - [Kafka Streams Configuration](#kafka-streams-configuration)
  - [Application Configuration](#application-configuration)
- [Input & Output Topics](#input--output-topics)
  - [Input Topics](#input-topics)
  - [Output Topics](#output-topics)
- [Multi-Tenancy](#multi-tenancy)
- [Kafka Streams Topology](#kafka-streams-topology)

## Overview

The Workflow Diffuser (WFD) is a core component of the LittleHorse StreamSense product. It reads the LH Output Topic — a Kafka topic containing real-time updates from workflow execution — and "diffuses" it into distinct entity-style `Streamlet`s for different metadata types:

- **WfSpec Streamlets** - workflow runs as queryable entities with variables as fields
- **ExternalEventDef Streamlets** - external event payloads
- **UserTaskDef Streamlets** - user task data
- **WorkflowEventDef Streamlets** - workflow event payloads

The WFD is a standalone Kafka Streams application (no Quarkus, no application framework). It uses SmallRye Config for configuration management and exposes health and metrics endpoints via a plain Java HTTP server.

## Tech Stack

| Component         | Technology                     | Version |
| ----------------- | ------------------------------ | ------- |
| Language          | Java                           | 25      |
| Build             | Gradle (Kotlin DSL)            | latest  |
| Stream Processing | Apache Kafka Streams           | 4.1.1   |
| Configuration     | SmallRye Config + YAML source  | latest  |
| Metrics           | Micrometer + Prometheus        | latest  |
| LH Client         | LittleHorse Java Client        | 0.16.0  |

## Dependencies

```kotlin
// build.gradle.kts
dependencies {
    implementation("org.apache.kafka:kafka-streams:4.1.1")
    implementation("io.smallrye.config:smallrye-config")
    implementation("io.smallrye.config:smallrye-config-source-yaml")
    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("io.littlehorse:littlehorse-client:0.16.0")
}
```

## Application Architecture

### Package Structure

```
io.littlehorse.streamsense.diffuser
├── WorkflowDiffuserApp.java          # Main entry point
├── config/
│   ├── AppConfig.java                # Application config mapping
│   ├── KafkaStreamsConfig.java        # Kafka Streams config mapping
│   ├── ConfigFactory.java            # SmallRye Config builder
│   └── LhEnvConfigSource.java        # Custom LH_ env var config source
├── server/
│   ├── HttpServerFactory.java        # HTTP server setup
│   ├── HealthHandler.java            # Health endpoint handler
│   └── MetricsHandler.java           # Metrics endpoint handler
├── topology/
│   └── ...                           # Kafka Streams topology (TBD)
└── serde/
    └── ...                           # Custom serdes (TBD)
```

### Application Bootstrap

The application is a vanilla Java `main` method. No Quarkus, no Spring, no framework — just plain Java orchestrating SmallRye Config, Kafka Streams, Micrometer, and the JDK HTTP server.

```java
public class WorkflowDiffuserApp {

    public static void main(String[] args) {
        // 1. Build configuration
        SmallRyeConfig config = ConfigFactory.build();
        AppConfig appConfig = config.getConfigMapping(AppConfig.class);
        KafkaStreamsConfig ksConfig = config.getConfigMapping(KafkaStreamsConfig.class);

        // 2. Build Kafka Streams topology
        Topology topology = TopologyFactory.build(appConfig);

        // 3. Create KafkaStreams instance
        KafkaStreams streams = new KafkaStreams(topology, ksConfig.asProperties());

        // 4. Setup Micrometer metrics
        PrometheusMeterRegistry registry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        new KafkaStreamsMetrics(streams).bindTo(registry);

        // 5. Start HTTP server (metrics + health)
        HttpServer server = HttpServerFactory.create(streams, registry, appConfig);
        server.start();

        // 6. Register shutdown hook
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            streams.close(Duration.ofSeconds(30));
            server.stop(5);
        }));

        // 7. Start Kafka Streams
        streams.start();
    }
}
```

### HTTP Server

A plain `com.sun.net.httpserver.HttpServer` exposes metrics and health endpoints. No framework needed.

| Endpoint             | Method | Description                        |
| -------------------- | ------ | ---------------------------------- |
| `/metrics`           | GET    | Prometheus metrics (text format)   |
| `/health`            | GET    | Combined health status             |
| `/health/liveness`   | GET    | Liveness probe                     |
| `/health/readiness`  | GET    | Readiness probe                    |

Default port: **8080** (configurable via `app.server.port`).

Response format for health endpoints:

```json
{
  "status": "UP",
  "kafkaStreams": {
    "state": "RUNNING"
  }
}
```

Status codes:
- `200 OK` when healthy
- `503 Service Unavailable` when unhealthy

### Health Checks

Health is derived from `KafkaStreams.State`:

| Probe       | Healthy States                                       | Purpose                                   |
| ----------- | ---------------------------------------------------- | ----------------------------------------- |
| **Readiness** | `RUNNING`, `REBALANCING`                            | Can accept and process records            |
| **Liveness**  | `CREATED`, `RUNNING`, `REBALANCING`, `PENDING_SHUTDOWN` | Process is alive, should not be restarted |

The `/health` endpoint reports `UP` when **both** liveness and readiness are healthy.

Unhealthy states (liveness = down, triggers restart):
- `NOT_RUNNING`
- `PENDING_ERROR`
- `ERROR`

### Metrics

Micrometer with the Prometheus registry collects and exposes metrics:

1. **Kafka Streams metrics** - bound via `KafkaStreamsMetrics` (thread-level, task-level, node-level, and state store metrics from Kafka Streams' built-in `Metric`s)
2. **JVM metrics** - memory, GC, threads (via Micrometer's `JvmMemoryMetrics`, `JvmGcMetrics`, `JvmThreadMetrics`)
3. **Application metrics** - custom counters/gauges as needed (TBD with topology)

The `/metrics` endpoint returns the Prometheus text exposition format (`text/plain; version=0.0.4`), scraped by standard Prometheus collectors.

## Configuration

### SmallRye Config Setup

Configuration is built programmatically using `SmallRyeConfigBuilder`. No Quarkus injection — the config is constructed in `ConfigFactory` and passed explicitly to components.

```java
public class ConfigFactory {

    public static SmallRyeConfig build() {
        SmallRyeConfigBuilder builder = new SmallRyeConfigBuilder()
                .addDefaultInterceptors()
                .addDefaultSources()
                // Classpath YAML sources
                .withSources(new YamlConfigSource("application.yaml"))
                .withSources(new YamlConfigSource("application.yml"))
                // Custom LH_ environment variable source
                .withSources(new LhEnvConfigSource())
                // External config files from LH_CONFIG_FILES
                .withSources(externalConfigSources())
                // Config mappings
                .withMapping(AppConfig.class)
                .withMapping(KafkaStreamsConfig.class);

        return builder.build();
    }
}
```

### Configuration Precedence

Property sources are applied in this order (highest to lowest priority):

| Priority | Source                            | Ordinal | Description                              |
| -------- | --------------------------------- | ------- | ---------------------------------------- |
| 1        | `LH_` environment variables      | 400     | Env vars with `LH_` prefix              |
| 2        | External config files             | 300     | Files listed in `LH_CONFIG_FILES`        |
| 3        | `application.yml` (classpath)     | 260     | Bundled YAML config                      |
| 4        | `application.yaml` (classpath)    | 250     | Bundled YAML config                      |
| 5        | Config mapping defaults           | —       | `@WithDefault` values in config classes  |

### Environment Variable Mapping

Environment variables with the `LH_` prefix override all other configuration. The custom `LhEnvConfigSource` strips the prefix and converts to dotted property names:

```
LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS=broker:9092
  → strips LH_ prefix  → KAFKA_STREAMS_BOOTSTRAP_SERVERS
  → converts to dotted  → kafka.streams.bootstrap.servers
```

This allows standard Kubernetes/container deployment patterns:

```yaml
env:
  - name: LH_KAFKA_STREAMS_BOOTSTRAP_SERVERS
    value: "kafka:9092"
  - name: LH_APP_SERVER_PORT
    value: "9090"
```

### External Config Files

The `LH_CONFIG_FILES` environment variable accepts a comma-separated list of file paths to YAML config files. These are loaded as additional config sources with ordinal 300.

```bash
export LH_CONFIG_FILES=/etc/wfd/config.yaml,/etc/wfd/tenants.yaml
```

This supports mounting external configuration in containerized deployments without rebuilding the application.

### Kafka Streams Configuration

All Kafka Streams properties are loaded under the `kafka.streams` prefix and passed directly to the `KafkaStreams` constructor as a `Properties` object.

```yaml
kafka:
  streams:
    application.id: lh-workflow-diffuser
    bootstrap.servers: localhost:9092
    schema.registry.url: http://localhost:8081
    auto.offset.reset: latest
    default.key.serde: org.apache.kafka.common.serialization.Serdes$StringSerde
    default.value.serde: io.confluent.kafka.streams.serdes.avro.SpecificAvroSerde
    num.stream.threads: 2
```

Config mapping interface:

```java
@ConfigMapping(prefix = "kafka.streams")
public interface KafkaStreamsConfig {

    @WithName("application.id")
    @WithDefault("lh-workflow-diffuser")
    String applicationId();

    @WithName("bootstrap.servers")
    @WithDefault("localhost:9092")
    String bootstrapServers();

    @WithName("num.stream.threads")
    @WithDefault("2")
    int numStreamThreads();

    /**
     * Returns all kafka.streams.* properties as a Properties object
     * suitable for passing to the KafkaStreams constructor.
     */
    default Properties asProperties() { ... }
}
```

Any standard Kafka Streams configuration property can be set under `kafka.streams.*` — the full map is extracted and forwarded to Kafka Streams without hard-coding every property.

### Application Configuration

Enabled LH Tenants and their specs are loaded under the `app` prefix. A tenant is identified by name (String) and added dynamically. As long as a tenant is listed and not empty, it will be subscribed for processing.

```yaml
app:
  lh-cluster-id: my-cluster
  server:
    port: 8080
  tenants:
    - name: tenant1
      wf-specs:
        - order-processing
        - stock-update
      user-task-defs:
        - confirm-purchase
        - mark-shipped
      external-event-defs:
        - payment-confirmed
      workflow-event-defs:
        - order-shipped
    - name: tenant2
      wf-specs:
        - inventory-check
        - returns-processing
      user-task-defs:
        - approve-return
      external-event-defs: []
      workflow-event-defs: []
```

Config mapping interfaces:

```java
@ConfigMapping(prefix = "app")
public interface AppConfig {

    String lhClusterId();

    ServerConfig server();

    List<TenantConfig> tenants();

    interface ServerConfig {
        @WithDefault("8080")
        int port();
    }

    interface TenantConfig {
        String name();
        Optional<List<String>> wfSpecs();
        Optional<List<String>> userTaskDefs();
        Optional<List<String>> externalEventDefs();
        Optional<List<String>> workflowEventDefs();
    }
}
```

## Input & Output Topics

### Input Topics

The WFD consumes from two topics per configured tenant:

| Topic | Naming Pattern | Partitions | Cleanup Policy | Content |
| ----- | ------------- | ---------- | -------------- | ------- |
| Execution Output | `{lhClusterId}_{tenant}_execution` | Multi-partition | delete | `OutputTopicRecord` (protobuf) with `TaskRun`, `WfRun`, `UserTaskRun`, `Variable`, `ExternalEvent`, `WorkflowEvent` |
| Metadata Output  | `{lhClusterId}_{tenant}_metadata`  | 1 (single) | compact | `MetadataOutputTopicRecord` (protobuf) with `WfSpec`, `TaskDef`, `ExternalEventDef`, `WorkflowEventDef`, `UserTaskDef` |

The metadata topic is loaded as a **Global Store** in Kafka Streams for enrichment joins.

### Output Topics

The WFD produces to one topic per enabled Streamlet, per tenant. Topic naming follows the Saddle multi-tenancy convention:

```
{lhClusterId}_{tenant}_streamlet_{streamlet-name}
```

Examples for `tenant1` with the config above:

| Streamlet Type | Spec Name            | Output Topic                                                 |
|----------------| -------------------- |--------------------------------------------------------------|
| WfRun          | order-processing     | `cluster1_tenant1_streamlet_workflow-order-processing`       |
| WfRun          | stock-update         | `cluster1_tenant1_streamlet_workflow-stock-update`           |
| UserTask       | confirm-purchase     | `cluster1_tenant1_streamlet_usertask-confirm-purchase`       |
| ExternalEvent  | payment-confirmed    | `cluster1_tenant1_streamlet_externalevent-payment-confirmed` |
| WorkflowEvent  | order-shipped        | `cluster1_tenant1_streamlet_workflowevent-order-shipped`     |

Output topics are resolved at runtime using a `TopicNameExtractor` with Apicurio JSON Schema SerDes. See [details/dynamic-topic-routing.md](details/dynamic-topic-routing.md) for the routing mechanism. See [details/wfrun-store-eviction.md](details/wfrun-store-eviction.md) for the WfRun store eviction design.

### Streamlet Output Schemas

Output format for WfRun Streamlets:

```json
{
  "id": "<wfRunId>",
  "status": "<status>",
  "columns": {
    "user-id": "obi-wan",
    "order-status": "SHIPPING",
    "item-id": "lightsaber"
  },
  "createdAt": "<timestamp>",
  "updatedAt": "<timestamp>"
}
```

Concrete example from the `output-topic` WfSpec (successful completion with both public variables populated):

```json
{
  "id": "2f5fa6471cd346258a4da55ef45b93d3",
  "status": "COMPLETED",
  "columns": {
    "input-name": "Colt",
    "public-greeting": "Hello there, Colt"
  },
  "createdAt": "2026-02-10T16:04:33.531Z",
  "updatedAt": "2026-02-10T16:05:33.642Z"
}
```

Output format for ExternalEvent Streamlets:

```json
{
  "id": "<externalEventId>",
  "wfRunId": "<wfRunId>",
  "content": "<payload>",
  "claimed": "<boolean>",
  "createdAt": "<timestamp>"
}
```

Example from `name-event` ExternalEventDef (wfRunId `c788297c`):

```json
{
  "id": "c788297c849347609500f553eaf3b335/name-event/09481e551f6f44b2b0acbbd4abbd71a1",
  "wfRunId": "c788297c849347609500f553eaf3b335",
  "content": "Obi-Wan",
  "claimed": true,
  "createdAt": "2026-02-10T16:07:12.745Z"
}
```

Output format for UserTask Streamlets:

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
    "<field-name-1>": "<value>",
    "<field-name-2>": "<value>"
  },
  "createdAt": "<timestamp>"
}
```

Example from `it-request` UserTaskDef (wfRunId `c59560f5`, DONE snapshot):

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

Output format for WorkflowEvent Streamlets:

```json
{
  "id": "<workflowEventId>",
  "wfRunId": "<wfRunId>",
  "workflowEventDefName": "<name>",
  "content": "<payload or null>",
  "createdAt": "<timestamp>"
}
```

Example from `sleep-done` WorkflowEventDef (wfRunId `26ab000b`):

```json
{
  "id": "26ab000bba79445dbb85f4f5ceff7a30/sleep-done/0",
  "wfRunId": "26ab000bba79445dbb85f4f5ceff7a30",
  "workflowEventDefName": "sleep-done",
  "content": "hello there!",
  "createdAt": "2026-02-10T15:16:31.164Z"
}
```

Streamlet Value Schemas are registered in ApiCurio using the tenant-prefixed naming convention:
- Key schema: string
- Value schema: `{tenant-id}__{streamlet-name}-value`

## Multi-Tenancy

A single WFD cluster processes all configured WfSpecs across tenants. Multi-tenancy is configuration-driven — adding or removing tenants requires only a config update and rolling restart (no new pods).

Key conventions:
- **Input topic naming**: `{lhClusterId}_{tenant}_execution` / `{lhClusterId}_{tenant}_metadata`
- **Output topic naming**: `{lhClusterId}_{tenant}_streamlet_{streamlet-name}`
- **Consumer group**: `{applicationId}` (shared across tenants, Kafka Streams manages partitions)
- **Schema naming**: `{lhClusterId}_{tenant}_streamlet_{streamlet-name}-value` in ApiCurio
- **Per-tenant Kafka users**: with ACL restrictions on their prefixed topics

## Kafka Streams Topology

The topology uses a two-sub-topology design:

- **Sub-topology 0 — Metadata Processor**: Ingests metadata records, resolves tenants, and populates a `GlobalKTable` accessible to the diffuser processor.
- **Sub-topology 1 — Diffuser Processor**: Consumes execution records, enriches them with metadata from the global store, and routes output to dynamic tenant-scoped sink topics via `TopicNameExtractor`.

Processing is stateless for `ExternalEvent`, `WorkflowEvent`, and `UserTaskRun` (passthrough transform-and-forward), and stateful for `WfRun` and `Variable` (aggregated into `wfrun-store` with public variable filtering via metadata lookup).

For the full topology design — including state store schemas, dynamic topic routing, and open items — see [topology-design.md](topology-design/topology-design_v0.3.0.md).
