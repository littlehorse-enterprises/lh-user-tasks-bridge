# Embedded Workflow Diffuser

The backend includes the `diffuser` Gradle module. When enabled, the existing Spring Boot
process owns Kafka Streams, shuts it down with the application, and exports its health and
metrics through Actuator. The backend JAR and existing backend image are the combined
deployable; no additional diffuser process or container is required. The standalone diffuser
entry point remains available for development.

Diffusion is disabled by default, so existing user-task deployments can upgrade without
adding Kafka configuration. Enable it with `LHBC_DIFFUSER_ENABLED=true` (or Spring property
`business-console.diffuser.enabled=true`) and provide the tenant selection and Kafka settings
in an external YAML file through `LH_CONFIG_FILES`. The checked-in `config/diffuser.yml` is
an example for the existing demo workflow; it is not a configuration for every deployment.

## Configuration and prerequisites

- Set `app.lh-cluster-id` to the Kernel's `LHS_CLUSTER_ID`.
- Select tenants and exact WfSpec/UserTaskDef/ExternalEventDef/WorkflowEventDef names under
  `app.tenants`. Enabling the component without any tenants fails startup.
- Enable Output Topic recording for those tenants in the Kernel before starting Streams.
  For a local development tenant, `lhctl put tenant default --all-entity-events` enables all
  eligible records. Production recording and Kafka permissions must match the intended data.
- Set `kafka.streams.application.id`, `bootstrap.servers` and a writable persistent `state.dir`.
  Keep the application ID stable across restarts to preserve consumer offsets and store identity.
- Provision the current topology's global metadata changelog topic before first startup:
  `<application.id>-global-metadata-changelog`, with one partition and compaction. Set its
  replication factor to match the cluster. The default local example uses one replica.
- Kafka credentials/security properties go under `kafka.streams`; optional Apicurio settings
  go under `apicurio.registry`. Without a registry URL, output values are plain JSON. Values
  and topic naming retain the imported diffuser contract; this change does not register them
  as resources in Saddle.

Namespaced `diffuser-defaults.yaml` supplies component defaults without loading Spring's
`application.yml`. External YAML and existing `LH_` environment overrides retain the diffuser's
SmallRye configuration behavior. Prefer YAML for list entries and hyphenated keys; the custom
environment mapper converts underscores to dots. The optional Saddle config-provider plugin
and business-flow configuration described in the product proposal are future work.

## Local Compose overlay

Build with the root wrapper (`./gradlew build :diffuser:installDist`) and the existing frontend
build if starting the console. Use the same `LH_VERSION` as `./gradlew -q printLittleHorseVersion`.
The opt-in overlay configures the standalone Kernel's Kafka advertisement for the Compose
network and mounts persistent diffuser state into the existing backend:

```sh
export LH_VERSION=$(./gradlew -q printLittleHorseVersion --console=plain)
docker compose -f docker-compose.yaml -f docker-compose.diffuser.yaml up -d littlehorse
LHC_API_HOST=localhost LHC_API_PORT=2023 lhctl put tenant default --all-entity-events
docker compose -f docker-compose.yaml -f docker-compose.diffuser.yaml exec littlehorse \
  kafka-topics.sh --bootstrap-server localhost:9092 --create --if-not-exists \
  --topic cluster1-wf-diffuser-global-metadata-changelog --partitions 1 \
  --replication-factor 1 --config cleanup.policy=compact
docker compose -f docker-compose.yaml -f docker-compose.diffuser.yaml up -d --build
```

Kernel Output Topics must exist before the diffuser starts. Enable recording before registering
the workflow definitions needed for metadata. Existing definitions may need to be re-registered
to populate metadata for a newly enabled Output Topic. The default execution consumer starts at
the latest offset; this integration does not backfill old runs. The included example selects
the existing `user-tasks-bridge-demo` workflow and `user-tasks-form` user task.

For a host-run backend, use an external YAML file whose Kafka bootstrap address and state
directory are accessible from that host, and set `LHUT_OIDC_CONFIG_FILE_LOCATION` as for the
existing backend. The component does not start its standalone HTTP server inside Spring.
`/actuator/health/readiness` includes diffuser state when enabled; `/actuator/prometheus`
includes Kafka Streams metrics. Operational metrics are distinct from the business metrics
and interactive-query APIs in the product proposal.

## Verification and imported history

```sh
./gradlew build :diffuser:installDist
./gradlew :diffuser:integrationTest # requires Docker; Kafka/Apicurio integration coverage
```

The diffuser source was imported from commit
`2ead3bb3b441e3e8cafef85057c9ef79356f38a9` as a second merge parent, without squashing or
rewriting its original commits. The import commit relocates the tree into `diffuser/`; original
historical paths remain visible in the original commits. Check ancestry with:

```sh
git merge-base --is-ancestor 2ead3bb3b441e3e8cafef85057c9ef79356f38a9 HEAD
git log 2ead3bb3b441e3e8cafef85057c9ef79356f38a9
```

The integration PR must land with ancestry intact. Squash or rebase merging the PR would lose
the original diffuser ancestry from `main`. If the repository permits only squash merges,
a maintainer must arrange an ancestry-preserving landing before merging this PR.
