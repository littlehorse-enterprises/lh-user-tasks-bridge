# LittleHorse Glossary

This section contains some terms that we use internally. This page is useful for new employees and also veterans—it includes some new concepts that we will introduce over the coming months.

We may put a customer-friendly version of this page on our docs.

- [LittleHorse Glossary](#littlehorse-glossary)
  - [The Basics](#the-basics)
    - [Core LittleHorse](#core-littlehorse)
    - [Delivery Methods](#delivery-methods)
    - [Control Plane and Data Planes](#control-plane-and-data-planes)
  - [Open-Source Platform Components](#open-source-platform-components)
    - [The Kernel](#the-kernel)
      - [`Getable`s](#getables)
    - [Pony ID](#pony-id)
    - [Gallop Streams](#gallop-streams)
    - [LH Connect](#lh-connect)
    - [LH Runtime](#lh-runtime)
  - [Saddle](#saddle)
    - [The Kernel](#the-kernel-1)
    - [Pony ID](#pony-id-1)
    - [Gallop Streams](#gallop-streams-1)
    - [LH Connect](#lh-connect-1)
    - [LH Runtime](#lh-runtime-1)
  - [The Operator](#the-operator)

## The Basics

The company started out with "just" a workflow engine. Before we expanded our vision to include more products, most of our glossary fit in just this section.

### Core LittleHorse

* **LH Kernel**: The marketing term / product name referring to the open-source workflow engine in the [`littlehorse` repo](https://github.com/littlehorse-enterprises/littlehorse). It includes both the Server and the Dashboard (as well as all of the relevant clients, like `sdk-java`, `sdk-dotnet`, `lhctl`, etc.).
* **The Server**: The backend of the LH Kernel (i.e. the part that exposes a GRPC Server on `localhost:2023` in the `lh-standalone` image).
  * A **LittleHorse Server Instance** is a single JVM running the Jar files for the LH Kernel.
* **LH Dashboard**: refers to the dashboard that ships with the open-source LittleHorse Kernel (`localhost:8080` in the `lh-standalone` image).
* **LH Cluster**: A technical term referring to cluster of LH Servers working together to provide a unified API to clients.
* **LH Operator**: The Kubernetes Operator in the [`lh-operator` repo](https://github.com/littlehorse-enterprises/lh-operator) that runs all of the various components of the LH Platform.
* **LH Platform**: The comprehensive platform that we provide to our customers, built on and around the open-source LH Kernel, Kafka, Keycloak, and other systems.
* **Pony ID**: The project in the [`lh-user-tasks-bridge-backend` repo](https://github.com/littlehorse-enterprises/lh-user-tasks-bridge-backend) that connects customer IdP's to the LH Kernel's User Tasks feature.

### Delivery Methods

Delivery Methods are different vectors through which we provide a product to our customers.

* **LH Cloud**: Delivered as a SaaS Service running within our own environment.
  * **LH Cloud Dedicated**: a full Data Plane dedicated to a single customer, often with VPC Peering.
  * **LH Cloud Enterprise**: a single Data Plane (Kubernetes Cluster) serving multiple customers, where each customer gets their own Pods (instance of the LH Platform). Generally, each customer gets a dedicated namespace in the backing Kubernetes Cluster in LH Cloud Enterprise (but this is an implementation detail that they won't see).
  * **LH Cloud Serverless**: an LH Cloud delivery method in which adding a new customer does not require deploying new pods (i.e. fully multi-tenant).
* **LH BYOC**: Delivered as a fully-managed SaaS service, _but_ with the platform running completely inside the customer's VPC where we have no break-glass access.
* **LH for Kubernetes**: Delivered through our Kubernetes Operator and run inside the customer's own infrastructure.

### Control Plane and Data Planes

These are some terms we use to describe where code actually runs, especially in (but not limited to) LittleHorse Cloud. They are internal-only and not used in front of customers.

* **Data Plane**: A Kubernetes Cluster in LH Cloud in which customer-facing workloads run.
* **Control Plane**: The single Kubernetes Cluster in LH Cloud that runs all of the software to run + monitor the entire fleet of LH Cloud (and in the future, LHK) clusters.
* **LHK Data Plane**: A Kubernetes cluster run by and owned by the customer that contains the LH Operator.

## Open-Source Platform Components

The **LH Platform** refers to the product capabilities (open-source or enterprise) that we provide to our users. It includes more than "just" the LH Kernel.

This section will list out the _open-source_ components of our LittleHorse platform.

* **The Kernel:** the distributed, code-first workflow engine in the `littlehorse` repo.
* **Pony ID:** connects your users to your workflows. Includes an IdP product and the User Tasks Bridge. (Note: this is currently not open-source.)
* **Gallop Streams:** a stream processing solution to extract real-time business insights from your workflow data and LH Connect data. Based on Kafka Streams.
* **LH Connect:** a framework for capturing events into Apache Kafka from the outside world and for triggering or notifying workflows in the Kernel based on those events.
* **LH Runtime:** a framework for building applications that use the Kernel, Pony ID, LH Connect, and Gallop Streams in Java. Based on Quarkus.

### The Kernel

Note that `Server` and `Dashboard` are defined above.

* **Workflow Builder:** an extension and/or screen inside the Dashboard which allows users to build a `WfSpec` by clicking and dragging.
* **Output Topic**: a Kafka Topic that the LH Servers produce to. Contains meaningful updates to workflows, tasks, events, and variables in real-time.

#### `Getable`s

A `Getable` is an internal term that we use to describe API resources which can be retrieved from the LH Server (think: `lhctl get ...`). Not a customer-facing term.

Getable's have two types:

1. _Metadata_: like blueprints or templates (eg. `WfSpec`).
2. _Execution Data_: things that actually happen (eg. `WfRun`).

* **`WfSpec`**: a workflow specification.
* **`WfRun`**: a workflow run instance. Created when you run a `WfSpec`.
* **`TaskDef`**: a definition for a task (to be executed by a computer) that can be used in `WfSpec`s.
* **`TaskRun`**: an instance of a `TaskDef` being executed.
* **`ExternalEventDef`**: defines a type of event that can occur outside of the context of a `WfRun`. A `WfSpec` can interact with `ExternalEvent`s by waiting for them or by handling them as signals.
* **`ExternalEvent`**: an instance of an event. This is the execution analog of `ExternalEventDef`. Each `ExternalEvent` is associated with one specific `WfRun`.
* **`CorrelatedEvent`**: is a precursor to an `ExternalEvent`. A `CorrelatedEvent` creates an `ExternalEvent` when it is correlated to a `WfRun` based on some key.
* **`WorkflowEvent`**: a type of event that can be _thrown by_ a `WfRun`. In contrast to an `ExternalEvent` (which a `WfRun` listens to), a `WfRun` _produces_ a `WorkflowEvent`.
* **`WorkflowEventDef`**: definition for a `WorkflowEvent`. This is the metadata analog.
* **`StructDef`**: a user-provided definition for a complex data type in LittleHorse (see `Struct` below).
* **`UserTaskDef`**: defines a task that is to be executed by a human user.
* **`UserTaskRun`**: an instance of a `UserTaskDef`.
* **`NodeRun`**: a running instance of a `Node`. See below for definition of a `Node`.

Next, some items which aren't  `Getable`s, but should be mentioned here:

* **`VariableValue`**: a value in a LittleHorse workflow.
* **`Struct`**: a `VariableValue` in LittleHorse whose value conforms to a `StructDef`. Just like a struct instance in go.
* **`ThreadRun`**: a `WfRun` consists of one or more `ThreadRun`s. The lifecycle of a `WfRun` mirrors the entrypoint `ThreadRun`. Each `ThreadRun` can execute one computational step at a time: if you want to do multiple things at once in a `WfRun`, you need multiple `ThreadRun`s.
* **`ThreadSpec`**: a `WfSpec` consists of one or more `ThreadSpec`s. A `ThreadSpec` is a directed graph of computational steps (`Node`s).
* **`Node`**: a `Node` represents a computational step within the graph of a `ThreadSpec`. Some types of `Node`s are Task nodes, User Task nodes, and External Event nodes. When a `ThreadRun` starts, it moves to the first `Node` in the `ThreadSpec`. Every time it reaches a `Node`, a `NodeRun` is created to track the computational step defined by the `Node`.

Lastly, some metadata `Getable`s which do not have anything to do with a workflow but rather are "cluster-level" metadata:

* **`Principal`**: defines the identity of some user (machine or human) of the LH Kernel and the associated permissions.
* **`Tenant`**: a logically isolated environment within the LH Kernel, like a namespace in kubernetes.

### Pony ID

Pony ID consists of two main parts:

1. **User Tasks Bridge**: a proxy that allows end users to easily execute `UserTaskRun`s inside the LH Kernel by logging in through an external IdP.
2. **LH IDP**: an opinionated but flexible IDP facade built on top of Keycloak which supports advanced Brokering and Federation capabilities.

Other important terms:

* **Brokering**: a practice in which the OIDC protocol is used to allow importing _user profiles_ in a just-in-time manner from an external Identity Provider into Pony ID. This allows Pony ID to know about users from an external IdP while users still only have to log into the external IdP.
* **Federation**: a practice in which the user login information, including credentials (!!), is imported into Pony ID's backing Keycloak instance from an external Identity Provider. It is strongly preferred to use Brokering.
* **`User`**: an end-user who executes `UserTaskRun`s. This is an API resource inside Pony ID. The ID of a `User` should match the `user_id` field in the `UserTaskRun` if UTB is in use.
* **Admin User**: a privileged `User` who can view and/or reassign _all_ `UserTaskRun`s and also CRUD `User`s. An Admin User doesn't have a special API Resource in Pony ID: it's just a `User` with a special OIDC role.

### Gallop Streams

Gallop Streams is a streaming platform that allows customers to _extract real-time insights_ and _react to events_ in LittleHorse. It is based on Apache Kafka and includes the following components:

* Apache Kafka, as a message broker.
* [ApiCurio Registry](https://www.apicur.io/registry) or another compatible Schema Registry to keep track of schemas.
* [Apache Kafka Streams](https://kafka.apache.org/streams) to extract real-time insights.

The core of Gallop Streams is _already publicly available_ in open-source (Kafka, ApiCurio, Kafka Streams). The only thing we add (in the open-source platform) is an extension to `lh-quarkus` that makes it easy to run a Kafka Streams Topology that uses the output topic and ApiCurio. We will also ship custom `Serde`s that facilitate development of `Topology`s using LittleHorse. Lastly, we'll have some integrations with Quarkus' REST framework to facilitate Interactive Queries.

### LH Connect

LH Connect will consist of four parts working together:

1. **Kafka Connect**, which is a framework for getting data into and out of Kafka from external systems.
2. A **Schema Registry** compatible with [ApiCurio Registry](https://www.apicur.io/registry) which keeps track of data types in Kafka topics.
3. The **LH Gateway**, which is a Quarkus-based API Gateway that receives and authenticates webhooks from external systems, and then produces records into Apache Kafka with valid schemas according to the ApiCurio Schema Registry for each webhook request.
4. The **LH Sink Connectors**, which are Kafka Sink Connectors that take data out of Apache Kafka and run workflows in LittleHorse with them.

In the analogy of "LH to MuleSoft," you can think of LH Connect as containing MuleSoft's API Gateway and connectors.

### LH Runtime

The LH Runtime is a framework (built on top of `lh-quarkus`, which is our Quarkus Extension) to develop, build, and run applications in Java that use:

* The LH Kernel,
* Pony ID,
* LH Connect, and
* Gallop Streams.

LH Runtime will be built on top of the open-source `lh-quarkus` extension and will include the following capabilities in the open-source:

* Register Task Workers, `TaskDef`s, and `WfSpec`s declaratively (already implemented).
* Produce records to Kafka in a way that is compatible with LH Connect.
* Register and run Kafka Streams `Topology`'s (that's a Kafka Streams term) for Gallop Streams.
* Build RESTful backends that are authenticated with Pony ID.

There will be _significant_ commercial extensions to LH Runtime in the paid products and Saddle.

## Saddle

Saddle is the administrative API and UI that orchestrates the entire LittleHorse Platform. It is _not_ open-source, but rather is a paid commercial product based on the open-source components described in the previous section. In this section, we will define terms in the Saddle API.

* **`SaddleTenant`**: A `SaddleTenant` is like a `Tenant` in the LH Kernel, but it refers to the entire LittleHorse Platform (including the Saddle dashboard). A `SaddleTenant` corresponds to a `Tenant` in the LH Kernel.
* **`SaddleUser`**: a `SaddleUser` is a user who can log into one or more `SaddleTenant`s.

### The Kernel

Let's dive into the Saddle API Resources that are related to the LH Kernel and are not open-source.

* **`Workflow`**: represents a single `WfSpec` in the overall LH Platform (beyond just the LH Kernel).
* **`KernelPrincipal`**: a Saddle resource that tracks a `Principal` in the LH Kernel. Allows Saddle administrators to manage LH Kernel users.

### Pony ID

Saddle's UI will allow administrators to configure the software infrastructure side of Pony ID: creation of the initial `AdminUser` and also configuration of Brokering or Federation.

* **Brokered IdP**: is an Identity Provider owned and managed by the customer that is brokered into Pony ID.
* **`BrokeredIdP`**: is an API Resource _In Saddle_ to track a Brokered IdP.
* **Group**: is a group of users in Pony ID. In the UTB, this matches the `user_group` field in the `UserTaskRun`.
* **`Group`**: is an API Resource _in Pony ID_ to keep track of the Group.

### Gallop Streams

Saddle will have significant value-add on top of open-source Apache Kafka for Gallop Streams. We will define the following entities:

* **`Schema`**: a structure for a piece of data (i.e. record in a Kafka topic). Refers to a `StructDef` and an ApiCurio schema.
* **`Streamlet`**: a resource in Saddle to track a Kafka Topic and the associated `Schema`.

In order to provide `Schema`s and `Streamlet`s to our users, we will provide an Apache Kafka service and an ApiCurio Registry service in all of our delivery methods (note that this will be difficult in LH Cloud Serverless). Under the hood we will likely need to use Kroxylicious to provide safeguards.

We will use the open-source `lh-quarkus` Kafka Streams extensions and build a few plug-and-play Kafka Streams `Topology`'s that perform some common use-cases. These use-cases will be available through CRD's and one-click deploys for our users:

* **`WorkflowSource`**: refers to a `Workflow` and creates a `Stream` by reading the output topic, filtering it, and enriching it. Each record represents a `WfRun` as an entity.
* **`WorkflowAlert`**: refers to a `Workflow` and creates alerts when it takes longer than a certain amount of time to finish, reaches a certain failure threshold, etc.

Lastly and most importantly, we will allow users to deploy Gallop Streams Apps against our Kafka service. We will have a CRD that integrates with the `lh-quarkus` Kafka Streams extensions to vastly improve ease of operations. The Gallop Streams apps can be deployed in the customer's environment or (perhaps) in LH Cloud. Hopefully, once KIP-1071 is finished, we will be able to automatically discover all Gallop Streams Apps running against our Kafka service. We will be able to show:

* The `Streams`s they listen to.
* All application instances and Stream `Task`s and their health, including standby replicas.
* Any operational recommendations (eg. add more instances, add disk space, watch a hot partition, etc).

We'll add this API Resource:

* **`GallopStreamsApp`**: an API Resource to track a Gallop Streams App.

Continue reading the LH Runtime to see further API Resources that allow users to _deploy_ Gallop Streams Apps through Saddle.

### LH Connect

Like Gallop Streams, most of the meat of LH Connect will be proprietary.

We'll start with Saddle API Resources related to the LittleHorse Sink Connectors for Apache Kafka. All resources contain a reference to a `Stream`, a reference to a `Workflow`, and some configurations related to Transformations:

* **`WorkflowTrigger`**: a Saddle resource tracking a Kafka Connector that reads from a `Stream` and runs a `WfSpec` using the `WfRunSinkConnector`.
* **`ExternalEventTrigger`**: a Saddle resource tracking a Kafka Connector that reads from a `Stream` and posts an `ExternalEvent` using the `ExternalEventSinkConnector`.
* **`CorrelatedEventTrigger`**: a Saddle resource tracking a Kafka Connector that reads from a `Stream` and posts a `CorrelatedEvent` using the `CorrelatedEventSinkConnector`.

Next, we will allow users to take advantage of regular Kafka Connectors to create `Stream`s and write their `Stream`s to external systems:

* **`KCSource`**: a Saddle resource tracking a Kafka Source Connector that creates a `Stream`.
* **`KCSink`**: a Saddle resource tracking a Kafka Source Connector that creates a `Stream`.

Lastly, we have an API Resource to define a webhook:

* **`WebhookSource`**: a Saddle Resource tracking an endpoint on the Saddle Backend which authenticates a webhook, validates its schema, and produces it to a `Stream`.

Note that all 6 LH Connect-related API Resources refer to `Stream`s. Also note that each `Stream` has an associated `Schema`. Therefore, by the transitive property, each LH Connect API Resource has a `Schema`.

### LH Runtime

The LH Runtime allows a "bring-your-own-code" capability to Saddle. We will support it in LHK at first, and in LH Cloud as an experiment if it goes well in LHK. This is part of what Van is talking about when he says "LH Is All You Need."

* **`SaddleJob`**: tracks a running process inside the LH Runtime. Can be a Task Worker, Web Backend, or a Gallop Streams app.
* **`SaddleWorker`**: refers to a Pod running in our environment which can run Saddle Jobs.
* **`SaddleWorkerPool`**: a logical group of Saddle Workers that allows users to group/segment 
* **`SaddleBag`**: tracks a plugin to LH Runtime. It is a JAR file that can be built and deployed by our `LHSaddleJob` CRD. Contains the logic for a Saddle Job, and refers to the `SaddleWorkerPool` where it's deployed.

A Saddle Job can contain a LH Gallop Streams app. The specification for a `SaddleBag` will be based on `lh-quarkus`.

Note that the LH Runtime will be a _huge component_ of the upcoming LittleHorse Marketplace, which is not yet well-defined.

## The Operator

Our Kubernetes Operator runs the whole LittleHorse Platform for our users. We have the following CRD's:

* **`LHCluster`**: runs a LittleHorse Cluster (the Kernel).
* **`LHDashboard`**: runs the dashboard.
* **`LHTenant`**: creates a `Tenant` inside a LH Cluster.
* **`LHPrincipal`**: creates a `Principal` inside a LH Cluster.
* **`LHKafka`**: runs Apache Kafka and also Kafka Connect.
* **`KafkaTopic`**: creates a Kafka Topic (note: this is a Strimzi CRD, not a LH CRD).
* **`LHKafkaUser`**: creates a Kafka User in a Kafka Cluster.
* **`LHKafkaConnector`**: manages a Kafka Connector in a Kafka Connect cluster associated with an `LHKafka`.
* **`LHKeycloak`**: runs a Keycloak cluster.
* **`LHKeycloakRealm`**: creates a `Realm` inside Keycloak.
* **`LHKeycloakClient`**: creates a client inside a Keycloak cluster.
* **`LHKeycloakOIDCProvider`**: configures brokering for an OIDC provider in a Keycloak Realm.
* **`LHCanaryAggregator`**: runs the "Aggregator" portion of the LH Canary.
* **`LHCanaryMetronome`**: runs the "Metronome" portion of the LH Canary.

We will need to add a few other CRD's:

* CRD's to run the User Tasks Bridge (Pony ID).
* CRD's to run the Saddle Backend.
* CRD's to run the LH Runtime (both for web backends, Gallop apps, Task Workers, and other use-cases).
  * **`LHSaddleJob`**: starts a Saddle Job. Similar to `KafkaConnector` CRD from Strimzi.
  * **`LHSaddleWorkerPool`**: creates a Saddle Worker Pool. Similar to `KafkaConnect` CRD from Strimzi.
