# LittleHorse SSO Workflow Bridge

This repository contains the code for:

- `SSO Workflow Bridge UI` (Next.js)
- `@littlehorse-enterprises/sso-workflow-bridge-api-client` (Node Package That Interacts With The SSO Workflow Bridge API)

This repository will help you interact with LittleHorse's SSO Workflow Bridge API.

## Overview

The LH UserTask UI provides a complete solution for managing human tasks within LittleHorse workflows. It consists of:

1. A modern web interface built with Next.js for managing and interacting with user tasks
2. A TypeScript API client that simplifies integration with the UserTasks API
3. Integration with Keycloak or any OIDC provider for secure authentication and authorization

This project is designed to work seamlessly with LittleHorse's workflow engine, allowing organizations to:

- Manage human-driven tasks within automated workflows
- Assign and track tasks for individuals or groups
- Monitor task progress and completion
- Maintain security through OIDC authentication

## Quickstart with Standalone Image

### Prerequisites for Quickstart

- [Docker](https://www.docker.com/)
- [httpie](https://httpie.io/) (for testing commands)
- [jq](https://jqlang.github.io/jq/) (for testing commands)
- [lhctl](https://littlehorse.dev/docs/getting-started/installation) (for testing commands)
- [openssl](https://www.openssl.org/) (for SSL certificates)

The fastest way to get started is using our standalone image that includes all necessary components:

```bash
docker run --name lh-sso-workflow-bridge-standalone --rm -d \
        -p 2023:2023 \
        -p 8080:8080 \
        -p 8888:8888 \
        -p 8089:8089 \
        -p 3000:3000 \
        ghcr.io/littlehorse-enterprises/sso-workflow-bridge/sso-workflow-bridge-standalone:main
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
lhctl run sso-workflow-bridge-demo user $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/users/?username=my-user" | jq -r ".[0].id")

# Assign to users group
lhctl run sso-workflow-bridge-demo group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=users" | jq -r ".[0].id")

# Assign to admins group
lhctl run sso-workflow-bridge-demo group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=admins" | jq -r ".[0].id")
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
git clone https://github.com/littlehorse-enterprises/sso-workflow-bridge.git
cd sso-workflow-bridge
```

2. Install git hooks:

```bash
pre-commit install
```

3. Create environment configuration:

   Copy `ui/.env.sample` as `ui/.env.local` and configure with:

```bash
AUTH_URL='http://localhost:3000'
AUTH_SECRET='<any secret here>'
AUTH_KEYCLOAK_HOST='http://localhost:8888'
AUTH_KEYCLOAK_REALM='default'
AUTH_KEYCLOAK_CLIENT_ID='sso-workflow-bridge-client'
AUTH_KEYCLOAK_CLIENT_SECRET=' '
LHUT_API_URL='http://localhost:8089'
```

1. Install dependencies and start development server:

```shell
npm install
```

```shell
npm run dev -w ui
```

In another terminal, start the API Client:

```shell
npm run dev -w api-client
```

The API Client will start listening to any live changes in the `api-client` folder and recompile it.

The UI will start with watch mode on <http://localhost:3000>

### Useful Links

- SSO Workflow Bridge UI: <http://localhost:3000>
- LittleHorse Dashboard: <http://localhost:8080>
- Keycloak Admin Console: <http://localhost:8888>

## Running with SSL

To run the UI with SSL enabled, you'll need to:

1. Generate SSL certificates using the provided script:

```bash
./local-dev/issue-certificates.sh
```

This script will:

- Create a `ssl` directory if it doesn't exist
- Generate a self-signed certificate (`cert.pem`) and private key (`key.pem`)
- Set up the certificates with a 10-year validity period
- Configure them for localhost usage

2. Run the container with SSL enabled:

```bash
docker run --rm \
    -e SSL=enabled \
    -v ./local-dev/ssl:/ssl \
    -e AUTH_URL='https://localhost:3443' \
    -e AUTH_SECRET='your-secret-here' \
    -e AUTH_KEYCLOAK_HOST='http://localhost:8888' \
    -e AUTH_KEYCLOAK_REALM='default' \
    -e AUTH_KEYCLOAK_CLIENT_ID='sso-workflow-bridge-client' \
    -e AUTH_KEYCLOAK_CLIENT_SECRET=' ' \
    -e LHUT_API_URL='http://localhost:8089' \
    -p 3000:3000 -p 3443:3443 \
    ghcr.io/littlehorse-enterprises/lh-sso-workflow-bridge/lh-sso-workflow-bridge-ui:main
```

When SSL is enabled, the UI will be available on:

- HTTP: <http://localhost:3000>
- HTTPS: <https://localhost:3443>

### Environment Variables for SSL

| Variable | Description | Required |
|----------|-------------|----------|
| `SSL` | Set to `enabled` to enable SSL | Yes |
| `NEXTAUTH_URL` | Full URL where the app will be accessible (use HTTPS port) | Yes |
| `NEXTAUTH_SECRET` | Random string used to hash tokens | Yes |
| `KEYCLOAK_HOST` | Keycloak server URL | Yes |
| `KEYCLOAK_REALM` | Keycloak realm name | Yes |
| `KEYCLOAK_CLIENT_ID` | Client ID from Keycloak | Yes |
| `KEYCLOAK_CLIENT_SECRET` | Client secret from Keycloak | Yes |
| `LHUT_API_URL` | URL of the User Tasks API | Yes |

### Notes

- For production environments, replace the self-signed certificates with proper SSL certificates
- The self-signed certificate will trigger browser warnings - this is expected for local development
- Make sure your Keycloak configuration includes the HTTPS URL in the allowed redirect URIs
