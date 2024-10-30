# This is for manual QA

Run project:
```shell
./local-dev/run.sh
```

Register workflow:
```shell
./gradlew demo:run
```

Assign a task to group (`admins` or `users`):
```shell
lhctl run user-tasks group users
```

Assign a task to `my-user`:
```shell
export KEYCLOAK_ADMIN_ACCESS_TOKEN=$(http --ignore-stdin --form "http://localhost:8888/realms/master/protocol/openid-connect/token" \
    client_id=admin-cli \
    username="admin" \
    password="admin" \
    grant_type=password | jq -r ".access_token")
lhctl run user-tasks user $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/users/?username=my-user" | jq -r ".[0].id")
```

Open user task ui: http://localhost:3000

Open lh dashboard: http://localhost:8080

Open keycloak: http://localhost:8888

Users:

| User          | Password |
|---------------|----------|
| my-admin-user | 1234     |
| my-user       | 1234     |
