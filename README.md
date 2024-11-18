# LH UserTask UI

This repository contains the code for:

- `UserTasks UI` (Next.js)
- `@littlehorse-enterprises/lh-user-tasks-api-client` (Node Package That Interacts With The UserTasks API)

This repository will help you interact with LittleHorse's UserTask API.

## Quickstart with Standalone Image

### Prerequisites for Quickstart

- [Docker](https://www.docker.com/)
- [httpie](https://httpie.io/) (for testing commands)
- [jq](https://jqlang.github.io/jq/) (for testing commands)
- [lhctl](https://littlehorse.dev/docs/getting-started/installation) (for testing commands)

The fastest way to get started is using our standalone image that includes all necessary components:

```bash
docker run --name lh-user-tasks-standalone --rm -d \
        -p 2023:2023 \
        -p 8080:8080 \
        -p 8888:8888 \
        -p 8089:8089 \
        -p 3000:3000 \
        ghcr.io/littlehorse-enterprises/lh-user-tasks-api/lh-user-tasks-standalone:main
```

This will start:

- LittleHorse Server (gRPC: 2023)
- LittleHorse Dashboard (<http://localhost:8080>)
- Keycloak (<http://localhost:8888>)
- User Tasks API (<http://localhost:8089>)
- User Tasks UI (<http://localhost:3000>)

## Available Users

### Keycloak Admin Console

To access the Keycloak admin console at <http://localhost:8888>, use:

- Username: **admin**
- Password: **admin**

### User Tasks UI

You can log in to the User Tasks UI at <http://localhost:3000> with these pre-configured users:

| User          | Password | Role  |
|---------------|----------|-------|
| my-admin-user | 1234     | Admin |
| my-user       | 1234     | User  |

## Testing User Tasks

You can test the user tasks functionality using these commands:

1. Export admin token:

```bash
export KEYCLOAK_ADMIN_ACCESS_TOKEN=$(http --ignore-stdin --form "http://localhost:8888/realms/master/protocol/openid-connect/token" \
    client_id=admin-cli \
    username="admin" \
    password="admin" \
    grant_type=password | jq -r ".access_token")
```

2. Create tasks for different users/groups:

```bash
# Assign to specific user
lhctl run user-tasks user $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/users/?username=my-user" | jq -r ".[0].id")

# Assign to users group
lhctl run user-tasks group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=users" | jq -r ".[0].id")

# Assign to admins group
lhctl run user-tasks group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=admins" | jq -r ".[0].id")
```

## Development Setup

### Prerequisites for Development

- [Node.js](https://nodejs.org/) (version 20 or later)
- [pre-commit](https://pre-commit.com/)
- [Git](https://git-scm.com/)

If you want to develop the UI locally:

### Setup

1. Clone this repository:

```bash
git clone https://github.com/littlehorse-enterprises/lh-user-tasks.git
cd lh-user-tasks
```

2. Install git hooks:

```bash
pre-commit install
```

3. Create environment configuration:

   Copy `ui/.env.sample` as `ui/.env.local` and configure with:

```bash
NEXTAUTH_URL='http://localhost:3000'
NEXTAUTH_SECRET='<any secret here>'
KEYCLOAK_HOST='http://localhost:8888'
KEYCLOAK_REALM='default'
KEYCLOAK_CLIENT_ID='user-tasks-client'
KEYCLOAK_CLIENT_SECRET=' '
LHUT_API_URL='http://localhost:8089'
```

1. Install dependencies and start development server:

```shell
npm install
```

```shell
npm run dev -ws ui -- --port 3001 # We use port 3001 to avoid conflict with the standalone image
```

In another terminal, start the API Client:

```shell
npm run dev -ws api-client
```

The API Client will start listening to any live changes in the `api-client` folder and recompile it.

The UI will start with watch mode on <http://localhost:3001>

### Useful Links

- User Tasks UI: <http://localhost:3001>
- LittleHorse Dashboard: <http://localhost:8080>
- Keycloak Admin Console: <http://localhost:8888>
