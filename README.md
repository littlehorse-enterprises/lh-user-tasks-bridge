# LittleHorse Business Console

The LittleHorse Business Console helps business experts shape,
run, and monitor their workflow and understand the data those workflows produce.
LittleHorse orchestrates workflow execution; the console aims to give business
users a unified view of progress and insights.

This repository contains:

- `console/` — Next.js and React web application.
- `backend/` — Java and Spring Boot API serving the dat for the web application.
- `api-client/` — TypeScript client for the backend API.
- `local-dev/`, `demo-workflow/`, and `standalone/` — local development tooling,
  a demo workflow, and an all-in-one demo image.

## Run locally

Install Java 21, Node.js 20.9 or later, pnpm 10.19.0, and Docker with Compose.
Add `127.0.0.1 keycloak littlehorse` to `/etc/hosts`, then run:

```sh
./local-dev/compose.sh up
```

Open the console at <http://localhost:3000>. Demo users are `my-user` and
`my-admin-user`, both with password `1234`.

See the [development and configuration guide](backend/README.md) for more details,
the [API client documentation](api-client/README.md) for integrations, and the
[standalone guide](standalone/README.md) for the demo image.

## Roadmap

Planned features for future releases:

- Configurable user task forms with a visual form builder.
- Permissions to control task visibility and group access.
- Reusable workflow templates and a generator for configuring, running, and
  adapting business processes.
- Custom workflow visualizations.
- Calculate and display Workflow metrics.
- Reports for querying workflow variables, task executions, and user task data.

## Formerly User Tasks Bridge

This project evolved from the LittleHorse User Tasks Bridge, which provided an
API and web interface for managing human tasks in LittleHorse workflows, with
OIDC authentication and user and group management. Business Console builds on
that foundation and expands its scope. Some package names, configuration options,
and documentation will be progressively renamed in the future. 
