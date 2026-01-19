# This is for manual QA

## Prerequisites

- [LittleHorse cluster](https://littlehorse.io/docs/server)
- [lhctl](https://littlehorse.io/docs/server/developer-guide/install#littlehorse-cli)

## Run project

```sh
./gradlew run
```

Assign a task to nobody:

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
lhctl run user-tasks-bridge-demo user $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/users/?username=my-user" | jq -r ".[0].id")
```

Assign a task to `my-admin-user`:

```sh
lhctl run user-tasks-bridge-demo user $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/users/?username=my-admin-user" | jq -r ".[0].id")
```
