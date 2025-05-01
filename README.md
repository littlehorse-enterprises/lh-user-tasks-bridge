# User Tasks Bridge Backend

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
- 1 for user-tasks-bridge-console

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
