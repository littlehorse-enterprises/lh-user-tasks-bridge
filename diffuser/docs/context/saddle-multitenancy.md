# Saddle Multi-Tenancy

Multi-tenancy is important to the LH Platform for two reasons:

1. Enabling a cost-effective, self-service deployment model (LittleHorse Cloud Serverless).
2. Allowing isolation between different teams in large enterprise environments.

The LH Kernel supports multi-tenancy natively through `Principal`s and `Tenant`s. This ADR explains how we will mirror that functionality for the rest of the platform in Saddle. For context, we provide multiple capabilities to our customers:

* The LH Kernel API
* The Kafka API (part of Gallop Streams)
* Management of `Schema`s in Saddle (part of Gallop Streams).
* Ability to deploy `WorkflfowStream`s and `StreamletProcessor`s in Gallop Streams.
* Ability to configure `WebhookSource`s, `KCSource`s, and `KCSink`s in LH Connect.
* Ability to deploy `TaskConnector`s (pre-packaged Task Workers) in LH Connect.
* Ability to deploy `StreamletMirror`s in LH Connect.
* _(Upcoming)_ The ability to deploy `SaddleJob`s into `SaddleWorkerPool`s using the LH Runtime.

## Phase 1 Design

The above capabilities depend giving our users accesss to a few systems, some of which are multi-tenant and others of which are not:

* **Multi-Tenant**
  * LittleHorse Kernel
  * Keycloak
* **Not-So-Multi-Tenant**
  * Kafka Connect
  * _(Upcoming)_ LittleHorse Runtime (JVM-based)
    * Gallop Streams
* **Very Not Multi-Tenant**
  * Kafka
  * ApiCurio
  * Task Worker Connector (Pod per Worker)

This has some limitations which impact customers; however, we have designed it in a way that we can incrementally resolve those limitations over time without coding ourselves into a corner.

The Initial Proposal makes our lives simpler by relying upon native Apache Kafka ACL's and authentication. This will accelerate time-to-market but yield certain limitations from the customer perspective; however, in the long term we can remove the limitations.

The overall strategy is:

1. Rely on Kernel `Tenant`s and Keycloak `Realm`s where appropriate.
2. Use prefixes and native Kafka ACL's to isolate `SaddleTenant`s within Apache Kafka.
3. Use prefixes and ApiCurio `group`s to isolate `SaddleTenant`s .

### Saddle-to-Kernel Mapping

As decicded in the [Saddle IAM ADR](./003-saddle-littlehorse-iam.md), users will log in to Saddle using the same Identity Provider (eg. Keycloak Realm) as used for the LH Kernel.

There is a `Tenant` resource in the LH Kernel. Similarly, there will be a `SaddleTenant` resource in Saddle for every _user-facing_ `Tenant` in the LittleHorse Kernel. Note that not all `Tenant`s in the LH Kernel are user-facing: for example, we have an `internal` `Tenant` that is used for the Saddle `WfRun`'s. The `internal` `Tenant` will be shared across all `SaddleTenant`s, meaning that we only need a single `create-user-in-keycloak` Task Worker for example.

A Saddle User will have access to one or more `SaddleTenant`s, just like a `Principal` has access to one or more LH Kernel `Tenant`s.

### Gallop Streams

Gallop Streams includes a few components:

* Access to the Output Topic from the LH Kernel.
* Ability to read and write `Streamlet`s of events with a `Schema` (note that this means we provide the Kafka API).
* Ability to process `Streamlet`s by deploying a `StreamletProcessor`.

#### Kafka Access for `Streamlet`s

* Every `SaddleTenant` is reserved a prefix `{tenant-id}__` for all consumer groups and topics.
* Every `SaddleTenant` gets an `LHKafkaUser` (this is a Custom Resource) with ACL's for their scoped prefix.
  * The Kafka User gets quotas according to the configured tenant-wide quota
* We use the Kafka broker-side interceptor for Topic Creation Authorization to enforce certain "don't shoot yourself in the foot" defaults.
* The initial deliverable will not allow more than one client per `SaddleTenant`. This will be addressed in the follow-up.
* We rely on native Apache Kafka authentication (most likely SASL-SSL, but to be determined by a later ADR) rather than using Kroxylicious for authentication.

Short-term follow-up work can include:
* Using Apache Kafka's quota plugins to limit total broker-side storage for each `SaddleTenant` (note that Strimzi does something similar).
* Using Kroxylicious' Record Validation Filter to prevent people from producing malformed records to a `Streamlet`.

#### ApiCurio Access for `Schema`s

* Every `SaddleTenant` is assigned a `group` in ApiCurio with access scoped to the Tenant Prefix (to be confirmed in a later ADR).
* Every `SaddleUser` gets a user in ApiCurio.
* A `Schema` with id `car-value` gets the id `{tenant-id}__car-value` in ApiCurio.

Note that a single `Streamlet` will need two `Schema`s since the key and the value of each message can have a schema. For example, the `car` `Streamlet` in `my-tenant` would have two schemas in ApiCurio:
* `my-tenant__car-value`
* `my-tenant__car-key`

#### `StreamletProcessor`s

`StreamletProcessor` designs are still percolating in my head. I see one potential opportunity for multi-tenancy (i.e. creation of a `WorkflowStream` resource); however, I believe the vast majority of `StreamletProcessor`s will be non-multi-tenant. I will defer the design of the multi-tenant `WorkflowStream` processor to a future ADR but I can see a single Kafka Streams Processor which reads all of the output topics (pattern subscription) and creates filtered `Streamlet`s based on the output for each one.

Other custom `StreamletProcessor`s will not support multi-tenancy. Customers of LH Cloud Serverless will be able to deploy `StreamletProcessor`s; however, they will be billed hourly and not in a usage-based manner because creating a `StreamletProcessor` will result in new compute resources being deployed.

### LittleHorse Connect

LittleHorse Connect consists of:
* **LittleHorse Gateway**, which accepts web requests (webhooks), authenticates them, validates their content structure, and produces them to a `Streamlet`.
* **Kafka Source Connectors**, which connect to an external system (using Kafka Connect) and produce data to a `Streamlet`.
* **Kafka Sink Connectors**, which read data from a `Streamlet` and write each event to an external system (using Kafka Connect).
* **Task Connectors**, which are pre-built Task Workers that can execute tasks without requiring the user to write, build, and deploy their own Task Workers.

#### LH Gateway

LittleHorse Gateway will be a single deployable (or it will be baked into the Saddle API), and every Saddle Tenant will get its own HTTP Path-based prefix. For example:

```
/gateway/my-tenant/foo
/gateway/my-tenant/bar
/gateway/saul-tenant/asdf
/gateway/edu-tenant/fdsa
```

All of those endpoints will correspond to `WebhookSource` resources in the correct Saddle Tenant and will be implemented in the same 

#### Kafka Connectors

We will have a library of supported _and trusted_ Kafka Connectors that can be deployed in a multi-tenant environment. This will allow a `KCSource` and a `KCSink` from multiple Saddle Tenants to be deployed into the same `KafkaConnect` cluster. This allows creating new `KCSource` and `KCSink`'s without deploying more `Pod`s.

Obviously, we'll support the LittleHorse Sink Connectors, but we will also support other connectors such as the S3 Sink, Aiven's Iceberg Sink, the JDBC Sink, and also Debezium Source Connectors. We'll have a registry which will store these connectors. See the upcoming Marketplace ADR.

#### Task Connectors

At first, Task Connectors will not be multi-tenant. For a discussion of how they can be made multi-tenant (for Java at least), see the Future Work.

### Pony ID

User Tasks Backend can support multiple LittleHorse `Tenant`s, and Keycloak supports multiple `Realm`s.

_Open Question:_ Does the User Tasks Frontend support multi-tenancy like the LH Dashboard does? This may be hard with multiple identity providers.

## Future Work

There are a lot of limitations with the above; however, if we were able to deliver even the limited capabilities discussed above it would be a _massive_ boon to our business. Therefore, we will use incremental future optimizations to improve on it.

### Kroxy-Based Kafka Tenancy

We can use Kroxylicious to enforce quotas and authentication for Kafka Clients. Furthermore, Kroxylicious could hide the tenant based prefixes for Kafka topics. This will provide the illusion of having a "full cluster" to our users as they will be able to create topics with any names, and they can have multiple different clients that all share quotas.

### Multi-Tenant Task Connector Runtime

We can have a JVM that runs multiple Task Connectors using the `littlehorse-quarkus` runtime. See `SaddleWorkerPool` and `SaddleJob` in the [previous ADR's](./002-littlehorse-glossary.md#lh-runtime).

We will implement this; however, it's not urgent yet.

### Usage-Based Billing

We can do fully serverless billing by keeping track of:

* Kafka throughput
* LH Kernel Task executions (or similar)
* Kafka Connector throughput
* LH Gateway event counts

## A Note on Bring-Your-Own-Kafka

I pushed the engineering team to investigate the feasibility of "bring-your-own-kafka" for the LH Platform. We decided that it is not feasible for the following reasons:

* Risks inherent in depending upon the customer to properly configure and run Kafka (even in Confluent Cloud, they would have undue access to our internal topics).
* Risks of customers producing malformed messages to a `Streamlet` topic, which breaks our "walled garden" philosophy.
* Risk of Kafka version drift, which could jeopardize support for crucial features. For example, there are things coming up in Kafka 4.3.0 (follow-up to KIP-1071) which we will definitely need. I don't want to get stuck with an old version.
* This allows us to potentially have a fork of Kafka with proprietary features. For example, we may implement improvements to the Streams Group Protocol and rely on them in Gallop Streams.
* We can get data into and out of our customer's Kafka clusters.

However, depending on how our partnership with Confluent goes, we may end up selling a white-labeled product with them. That means that Confluent might co-sell the LH Platform...if this becomes reality, we might need to integrate more closely with Confluent.
