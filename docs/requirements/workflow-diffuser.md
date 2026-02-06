# Workflow Diffuser

The Workflow Diffuser will be a core component of the LittleHorse StreamSense product. The goals of the Workflow Diffuser are to read the Output Topic and provide the following capabilities, top priority first:

1. To transform raw `OutputTopicRecord`s into entity-style output records which capture business meaning from the `WfSpec`.
2. To provide a RESTful API that allows querying `WfRun`s as if they were objects with each variable as a field.

## Background

To understand the Output Topic, see these resources first:

* [Output Topic Proposal](https://github.com/littlehorse-enterprises/littlehorse/blob/master/proposals/001-output-topic.md) in the open-source LH Server.
* [Output Topic Example](https://github.com/littlehorse-enterprises/littlehorse/tree/master/examples/java/output-topic) in the open-source LH Server.
* The [Definition of Streamlet](./002-littlehorse-glossary.md#gallop-streams)

### Motivations

Users want to be able to ask the following questions of their workflows. Imagine I had the following `WfSpec`:

```java
var userId = wf.declareStr("user-id").asPublic();
var itemId = wf.declareStr("item-id").asPublic();
var orderStatus = wf.declareStr("order-status").withDefault("PENDING").asPublic();

wf.execute("charge-credit-card", userId, wf.execute("fetch-price", itemId));
wf.execute("ship-item", userId, itemId);
orderStatus.assign("SHIPPING");

wf.throwEvent("order-accepted");

wf.waitForEvent("item-delivered");
orderStatus.assign("COMPLETED");
```

I'd like to be able to do the following:

* How many orders did I process where `item-id == 'lightsaber'`?
* How much money did I get from `user-id == '1234'`?
* Are there any spikes in workflows? (i.e. more failures, more workflows running, latency spikes, etc).
* Create "cascading workflows," wherein the completion of one `WfRun` triggers another `WfRun` in a loosely-coupled manner.
* How long does it take on average for an item to be delivered? (for example, time from `status == SHIPPING` to `status == COMPLETED`).
* Create a `Streamlet` from `order-accepted` events (`WorkflowEventDef`).
* Create a `Streamlet` for the `item-delivered` events (`ExternalEventDef`).

The Workflow Diffuser is the _first step_ in answering all of the above questions. We will take the raw firehose output of the Output Topic, and turn it into distinct `Streamlet`s for each `WfSpec` or `TaskDef` or `WorkflowEventDef` or `ExternalEventDef`.

Future components of LittleHorse StreamSense will allow reading the resulting `Streamlet`s (probably with KSML) and using that info to answer the above questions.

## Public API's

The Workflow Diffuser should be a deployable Kafka Streams topology that listens to the Output Topic and _diffuses_ it to create many `Streamlet`s for different metadata:

* `WfSpec`s as a `Streamlet`
* `ExternalEventDef`s as a `Streamlet`
* `UserTaskDef`s as a `Streamlet`
* `WorkflowEventDef`s as a `Streamlet`

### Output `Streamlet`

The `ExternalEventDef` and `WorkflowEventDef` Streamlets are straightforward: they should just contain the payload. THe `WfSpec` is more tricky. For the workflow written above, I think the output should have the following structure:

```
{
  "id": <wfRunId>,
  "status": <status>,
  "columns": {
    "user-id": "obi-wan",
    "order-status": "SHIPPING",
    "item-id": "lightsaber"
  },
  "createdAt": <created_at>
}
```

#### Formats and Serializers

The output should be compatible with the `Schema` and `Streamlet` capabilities in Saddle. For now, that means using the ApiCurio Serdes. We should also use JSON at first, but design into the API the ability to use non-JSON formats. This will require coordination with the `Schema` structure in Saddle.

A `Schema` in Saddle contains a reference to two ApiCurio Schemas: one for the key, and one for the value. The outputs from the Workflow Diffuser should be valid `Streamlet`s in the Saddle API, which means that they should be compatible with that.

### Interactive Query (MAYBE)

It might be useful to add an interactive query endpoint that allows users to query workflows as JSON objects (see the structure from the output topics). Alternatively, this can go into the REST Gateway (I'm leaning to the second option due to allowing to re-use indexes in the LH Server).

It should integrate with other parts of the Saddle ecosystem:
* ApiCurio can probably know the API spec
* Keycloak can secure the endpoints with OAuth

## Implementation Considerations

### Configuring Multiple `WfSpec`s

The configurations of the Workflow Diffuser should support having only one single Workflow Diffuser cluster reading from the LH Server output topic, and producing multiple different `Streamlet`s as output. This way we don't need to add a new pod every time a customer wants a new workflow as an output topic: we just update the configuration on existing pods (a rolling restart is okay).

### Multi-Tenancy

Ideally, the Workflow Diffuser should be built to be multi-tenant so that it can consume from multiple Output Topics for different LittleHorse Tenants. It should comply with the [Saddle Multi-Tenancy](./008-saddle-multitenancy.md) specs.
