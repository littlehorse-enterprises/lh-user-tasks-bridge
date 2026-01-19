# LittleHorse User Tasks Bridge Backend

Backend component that serves as a proxy between any OIDC compliant identity provider and LittleHorse Kernel `UserTasks`.

## Getting Started

### Software requirements

The following software packages are needed to be installed:

- [Brew](https://brew.sh/)
- [Docker](https://www.docker.com/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Java 21](https://www.oracle.com/mx/java/technologies/downloads/)

Run the following command to install pre-commit
`brew install pre-commit`

### Quick start

First update your `/etc/hosts` file with next hostnames:

```text
127.0.0.1 keycloak
127.0.0.1 littlehorse
```

We are using Keycloak to work as a sample Identity Provider that will support User Tasks Bridge API by having a basic
identity provider configured. You can find a Docker Compose configuration
in `docker-compose.yaml`. You can run Keycloak, a local LH Kernel and the User Tasks Bridge API locally with this command:

  ```shell
  ./local-dev/run.sh
  ```

> Stop it with `docker compose down -v`

If the command above ended successfully, then you should see 4 containers running:

- 1 for Keycloak
- 1 for LittleHorse Standalone (LH Kernel and LH Dashboard)
- 1 for lh-user-tasks-bridge-backend
- 1 for lh-user-tasks-bridge-console

After that, the User Tasks Bridge Backend should be available on <http://localhost:8089>

Verify that Keycloak is up and running, and if that is the case, you can now access it through your browser
on <http://localhost:8888>

Keycloak's admin credentials:

- username: **admin**
- password: **admin**

Also, you will need an access token to be able to be granted authorized access into lh-user-tasks-bridge-backend endpoints.
In order to fetch said token, you can use the following cURL as is from the terminal, or you can import it
to your REST client tool of preference (Postman, Insomnia, Bruno, etc.)

```shell
curl --request POST \
--url http://localhost:8888/realms/default/protocol/openid-connect/token \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data client_id=user-tasks-bridge-client \
--data client_secret= \
--data username=my-user \
--data password=1234 \
--data grant_type=password
```

Once you have your access token handy, you are ready to hit lh-user-tasks-bridge-backend endpoints.

For example, you can hit the `/<tenant_id>/init` endpoint with the following cURL:

```shell
curl --request GET \
  --url http://localhost:8089/default/init \
  --header 'Authorization: Bearer replace-this-with-your-access-token'
```

### Using lhctl

Update your `~/.config/littlehorse.config` or export the next env variables:

 ```shell
  export LHC_API_HOST=localhost
  export LHC_API_PORT=2023
  export LHC_TENANT_ID=default
  ```

This is just an example, use the values that match your LittleHorse config

### OIDC Configuration

Make sure that you edit the `oidc-properties.yml` file located at `./config/` directory.

Here's what you need to modify in that file:

- _**iss**_: Here you need to paste your Identity Provider's issuer url.
- _**label-name**_: This field allows you to set a string that can be used in your UI to differentiate your
  identity providers configured with the same tenant.
- _**username-claim**_: This field is currently not being used, but it is part of the required configuration,
  so you can just leave it as is with the default value as _preferred_username_
- _**user-id-claim**_: This property allows you to set what claim you want to use as _userId_ when performing assignments.
  You can set 1 of the following values: EMAIL, PREFERRED_USERNAME or SUB.
- _**authorities**_: Within this property you MUST set at least 1 JSON path that indicates from where the roles are
  going to be found within the token's claims, and this is important to help the API differentiate between ADMIN
  and NON-ADMIN users.
- _**vendor**_: This indicates who is the vendor or identity provider in charge of the authentication of users for
  the previously set issuer. For now, Keycloak is the only vendor with access to all the features that this API
  provides.
- **tenant-id**: This property must match your Tenant that MUST be already created within LittleHorse Kernel.
- **client-id-claim**: This property specifies what claim should be used to fetch the corresponding client id from the access token.
- **clients**: Within this property you MUST set at least one client-id from your Keycloak realm from which your
  access tokens will be generated.

When you have your oidc-properties.yml properly configured, you will be ready to run User Tasks Bridge API from the source code.

This command will execute Spring Boot's run task:

  ```shell
  ./gradlew backend:bootRun
  ```

> In case of using the standalone image update the `standalone/backend-properties.yml` file.

### Access Swagger UI

In order to see OpenAPI Specs with Swagger UI, and after having User Tasks Bridge Backend running, you just need to go to your
web browser and access the following URL: <http://localhost:8089/swagger-ui/index.html>

### Users & Groups Management

There are some useful endpoints if you want to create, fetch, update and delete users and groups from a realm in your
configured Identity Provider.

#### Special Considerations to Manage Users & Groups

- IMPORTANT: These endpoints will **_only work with Keycloak_** as Identity Provider, for there's only one IdP adapter currently implemented.
- These endpoints are set to only allow Admin users to hit them
- In order to properly manage users, besides having the `lh-user-tasks-admin` role that identifies Users as Admins,
  they also need to have the `manage-users`, `view-clients` and `view-realm` roles assigned.

**In case that all Admin users were deleted, you will need to create at least 1 by using your Identity Provider's dashboard.**

## LittleHorse UserTasks Bridge Console

This repository also contains the code for:

- `UserTasks Bridge Console` (Next.js)
- `@littlehorse-enterprises/user-tasks-bridge-api-client` (Node Package)

This repository will help you interact with LittleHorse's UserTasks Bridge Backend.

### Overview

The console provides a complete solution for managing human tasks within LittleHorse workflows. It consists of:

1. A modern web interface built with Next.js for managing and interacting with UserTasks
2. A TypeScript API client that simplifies integration with the UserTasks API
3. Integration with Keycloak or any OIDC provider for secure authentication and authorization

This project is designed to work seamlessly with LittleHorse's workflow engine, allowing organizations to:

- Manage human-driven tasks within automated workflows
- Assign and track tasks for individuals or groups
- Monitor task progress and completion
- Maintain security through OIDC authentication

### Quickstart with Standalone Image

#### Prerequisites for Quickstart

- [Docker](https://www.docker.com/)
- [httpie](https://httpie.io/) (for testing commands)
- [jq](https://jqlang.github.io/jq/) (for testing commands)
- [lhctl](https://littlehorse.dev/docs/getting-started/installation) (for testing commands)
- [openssl](https://www.openssl.org/) (for SSL certificates)

The fastest way to get started is using our standalone image that includes all necessary components:

```bash
docker run --name lh-user-tasks-bridge-standalone --pull always --rm -d --net=host \
        ghcr.io/littlehorse-enterprises/lh-user-tasks-bridge-backend/lh-user-tasks-bridge-standalone:latest
```

This will start:

- LittleHorse Server (gRPC: 2023)
- LittleHorse Dashboard (<http://localhost:8080>)
- Keycloak (<http://localhost:8888>)
- UserTasks Bridge Backend (<http://localhost:8089>)
- UserTasks Bridge Console (<http://localhost:3000>)

### Available Users

#### Keycloak Admin Console

To access the Keycloak admin console at <http://localhost:8888>, use:

- Username: **admin**
- Password: **admin**

#### UserTasks Bridge Console

You can log in to the UserTasks Bridge Console at <http://localhost:3000> with these pre-configured users:

| User          | Password | Role  |
| ------------- | -------- | ----- |
| my-admin-user | 1234     | Admin |
| my-user       | 1234     | User  |

### Testing UserTasks

You can test the UserTasks functionality using these commands:

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
lhctl run user-tasks-bridge-demo user $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/users/?username=my-user" | jq -r ".[0].id")

# Assign to users group
lhctl run user-tasks-bridge-demo group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=users" | jq -r ".[0].id")

# Assign to admins group
lhctl run user-tasks-bridge-demo group $(http --ignore-stdin -b -A bearer -a "${KEYCLOAK_ADMIN_ACCESS_TOKEN}" "http://localhost:8888/admin/realms/default/groups/?exact=true&search=admins" | jq -r ".[0].id")
```

### Development Setup

#### Prerequisites for Development

- [Node.js](https://nodejs.org/) (version 20 or later)
- [pre-commit](https://pre-commit.com/)
- [Git](https://git-scm.com/)

If you want to develop the UI locally:

#### Setup

1. Clone this repository:

```bash
git clone https://github.com/littlehorse-enterprises/lh-user-tasks-bridge.git
cd lh-user-tasks-bridge
```

2. Install git hooks:

```bash
pre-commit install
```

3. Create environment configuration:

   Copy `console/.env.sample` as `console/.env.local` and configure with:

```bash
# Auth Configuration
AUTH_SECRET=any-secret-here # Run `npx auth secret` to generate a secret. Read more: https://cli.authjs.dev
AUTH_KEYCLOAK_ID=user-tasks-bridge-client
AUTH_KEYCLOAK_SECRET=
AUTH_KEYCLOAK_ISSUER=http://localhost:8888/realms/default

# User Tasks Bridge Configuration
LHUT_API_URL=http://localhost:8089
LHUT_AUTHORITIES=$.realm_access.roles,$.resource_access.*.roles
# LHUT_METRICS_PORT=9464
# LHUT_METRICS_DISABLED=false

```

1. Install dependencies and start development server:

```shell
npm install
```

```shell
npm run dev -w console
```

In another terminal, start the API Client:

```shell
npm run dev -w api-client
```

The API Client will start listening to any live changes in the `api-client` folder and recompile it.

The UI will start with watch mode on <http://localhost:3000>

#### Useful Links

- UserTasks Bridge Console: <http://localhost:3000>
- LittleHorse Dashboard: <http://localhost:8080>
- Keycloak Admin Console: <http://localhost:8888>

### Running with SSL

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
    -e LHUT_OAUTH_ENCRYPT_SECRET='your-secret-here' \
    -e LHUT_OAUTH_CLIENT_ID='user-tasks-client' \
    -e LHUT_OAUTH_CLIENT_SECRET=' ' \
    -e LHUT_OAUTH_ISSUER_URI='http://localhost:8888/realms/default' \
    -e LHUT_API_URL='http://localhost:8089' \
    -e LHUT_AUTHORITIES='$.realm_access.roles,$.resource_access.*.roles' \
    -p 3000:3000 -p 3443:3443 \
    ghcr.io/littlehorse-enterprises/lh-user-tasks-bridge/lh-user-tasks-bridge-console:latest
```

When SSL is enabled, the UI will be available on:

- HTTP: <http://localhost:3000>
- HTTPS: <https://localhost:3443>

#### Environment Variables for SSL

| Variable                  | Description                                                | Required |
| ------------------------- | ---------------------------------------------------------- | -------- |
| `SSL`                     | Set to `enabled` to enable SSL                             | Yes      |
| `AUTH_URL`                | Full URL where the app will be accessible (use HTTPS port) | Yes      |
| `AUTH_SECRET`             | Random string used to hash tokens                          | Yes      |
| `AUTH_KEYCLOAK_ID`        | Client ID from Keycloak                                    | Yes      |
| `AUTH_KEYCLOAK_SECRET`    | Client secret from Keycloak                                | Yes      |
| `AUTH_KEYCLOAK_ISSUER`    | Keycloak server URL                                        | Yes      |
| `LHUT_API_URL`            | URL of the UserTasks API                                  | Yes      |
| `LHUT_AUTHORITIES`        | Paths to extract roles from the token                      | Yes      |

#### Notes

- For production environments, replace the self-signed certificates with proper SSL certificates
- The self-signed certificate will trigger browser warnings - this is expected for local development
- Make sure your Keycloak configuration includes the HTTPS URL in the allowed redirect URIs

## License

<a href="https://spdx.org/licenses/SSPL-1.0.html"><img alt="SSPL LICENSE" src="https://img.shields.io/badge/covered%20by-SSPL%201.0-blue"></a>

All code in this repository is covered by the [Server Side Public License, Version 1](https://spdx.org/licenses/SSPL-1.0.html) and is copyright of LittleHorse Enterprises LLC.
