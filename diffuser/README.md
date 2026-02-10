# LittleHorse Workflow Diffuser

The **Workflow Diffuser** (WFD) is a Kafka Streams application that consumes the LittleHorse Output Topic and transforms raw execution and metadata records into queryable, tenant-scoped **Streamlets** — one per WfSpec, ExternalEventDef, UserTaskDef, and WorkflowEventDef. This enables downstream analytics, cascading workflows, and real-time business insights from LH workflow data.

Part of the [LH StreamSense](https://github.com/littlehorse-enterprises) product.

## Architecture

![Kafka Streams Topology](docs/design/topology-design/LittleHorse%20Workflow%20Diffuser%20v0.1.1.excalidraw.png)

## Repo Structure

```
docs/
  requirements/   # Product requirements
  design/         # Design docs (main design doc, topology, dynamic routing)
  context/        # Reference material (LH glossary, output topic spec, data analysis)
  utils/          # Tool references (lhctl)
```

See [docs/design/workflow-diffuser.md](docs/design/workflow-diffuser.md) for the full design document.
