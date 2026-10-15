# This is for manual QA

## Prerequisites

- [LittleHorse cluster](https://littlehorse.io/docs/server)
- [lhctl](https://littlehorse.io/docs/server/developer-guide/install#littlehorse-cli)

## Run project

Run these commands from `demo-workflow/`. Use JDK 21 and keep the local
LittleHorse server running. For the Compose stack, configure the CLI and SDK:

```sh
export LHC_API_HOST=localhost
export LHC_API_PORT=2023
export LHC_TENANT_ID=default
```

```sh
./gradlew demo-workflow:run
```

This registers the workflow and task definition; start a workflow separately
with one of the commands below. The repository's Docker quick start does both,
assigning its initial task to `my-user`.

The sample bridge configuration uses `user-id-claim: EMAIL`, so assign users by
email address. For `SUB`, use the Keycloak user ID; for `PREFERRED_USERNAME`, use
the username. Groups use their Keycloak group IDs.

Test an invalid assignment with neither a user nor a group (the workflow errors):

```sh
lhctl run user-tasks-bridge-demo
```

Assign invalid ids:

```sh
lhctl run user-tasks-bridge-demo user $(uuidgen)
lhctl run user-tasks-bridge-demo group $(uuidgen)
```

Export access token:

```sh
export KEYCLOAK_ADMIN_ACCESS_TOKEN=$(http --ignore-stdin --form "http://localhost:8888/realms/master/protocol/openid-connect/token" \
    client_id=admin-cli \
    username="admin" \
    password="admin" \
    grant_type=password | jq -r ".access_token")
```

Assign a task to `users`:

```sh
lhctl run user-tasks-bridge-demo group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=users" | jq -r ".[0].id")
```

Assign a task to `admins`:

```sh
lhctl run user-tasks-bridge-demo group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=admins" | jq -r ".[0].id")
```

Assign a task to `my-user`:

```sh
lhctl run user-tasks-bridge-demo user someemailaddress@somedomain.com
```

Assign a task to `my-admin-user`:

```sh
lhctl run user-tasks-bridge-demo user someotheremailaddress@somedomain.com
```
