# User Tasks Bridge API

Backend component that serves as a proxy between any OIDC compliant identity provider and LittleHorse Server `UserTasks`.

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
in `docker-compose.yaml`. You can run Keycloak, a local LH Server and the User Tasks Bridge API locally with this command:

  ```shell
  ./local-dev/run.sh
  ```

> Stop it with `docker compose down -v`

If the command above ended successfully, then you should see 4 containers running:

- 1 for Keycloak
- 1 for LittleHorse Standalone (LH Server and LH Dashboard)
- 1 for user-tasks-bridge-api
- 1 for user-tasks-bridge-ui

After that, the User Tasks Bridge API should be available on <http://localhost:8089>

Verify that Keycloak is up and running, and if that is the case, you can now access it through your browser
on <http://localhost:8888>

Keycloak's admin credentials:

- username: **admin**
- password: **admin**

Also, you will need an access token to be able to be granted authorized access into user-tasks-bridge-api backend endpoints.
In order to fetch said token, you can use the following cURL as is from the terminal, or you can import it
to your REST client tool of preference (Postman, Insomnia, Bruno, etc.)

```shell
curl --request POST \
--url http://localhost:8888/realms/default/protocol/openid-connect/token \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data client_id=user-tasks-client \
--data client_secret= \
--data username=my-user \
--data password=1234 \
--data grant_type=password
```

Once you have your access token handy, you are ready to hit user-tasks-bridge-api endpoints.

For example, you can hit the `/<tenant_id>/init` endpoint with the following cURL:

```shell
curl --request GET \
  --url http://localhost:8089/default/init \
  --header 'Authorization: Bearer replace-this-with-your-access-token'
```

### Using lhctl

Update your `~/.config/littlehorse.config` or export next variables:

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
- _**username-claim**_: This field is currently not being used, but it is part of the required configuration,
  so you can just leave it as is with the default value as _preferred_username_
- _**authorities**_: Within this property you MUST set at least 1 JSON path that indicates from where the roles are
  going to be found within the token's claims, and this is important to help the API differentiate between ADMIN
  and NON-ADMIN users.
- _**vendor**_: This indicates who is the vendor or identity provider in charge of the authentication of users for
  the previously set issuer. For now, Keycloak is the only vendor with access to all the features that this API
  provides.
- **tenant-id**: This property must match your Tenant that MUST be already created within LittleHorse Server.
- **client-id-claim**: This property specifies what claim should be used to fetch the corresponding client id from the access token.
- **clients**: Within this property you MUST set at least one client-id from your Keycloak realm from which your
  access tokens will be generated.

When you have your oidc-properties.yml properly configured, you will be ready to run User Tasks Bridge API from the source code.

This command will execute Spring Boot's run task:

  ```shell
  ./gradlew api:bootRun
  ```

> In case of using the standalone image update the `standalone/api-properties.yml` file.

### Access Swagger UI

In order to see OpenAPI Specs with Swagger UI, and after having User Tasks Bridge API running, you just need to go to your
web browser and access the following URL: <http://localhost:8089/swagger-ui/index.html>
