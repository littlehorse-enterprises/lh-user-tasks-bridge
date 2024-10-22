# UserTasks API

Backend component that serves as a proxy between User Tasks' UI component and a LittleHorse Server.

## Getting Started

### Software requirements

The following software packages are needed to be installed:

- [Brew](https://brew.sh/)
- [Docker](https://www.docker.com/)
- [Docker Compose](https://docs.docker.com/compose/install/)
- [Java 21](https://www.oracle.com/mx/java/technologies/downloads/)

Run the following command to install pre-commit
`brew install pre-commit`

## Running UserTasks API locally

First, let's list out which services or software components are required in order to run UserTasks API locally.

- Run a LittleHorse Server
- Run an Identity Provider compatible with OpenId Connect (OIDC) protocol (in this case, we provide a preconfigured
  Keycloak)

### IMPORTANT!

##### Make sure that your LittleHorse configurations are taken from either the littlehorse.config file or respective environment variables. Avoid combining them, because they will clash with one another.

[Here](https://littlehorse.dev/docs/developer-guide/client-configuration/) you will find out more about LittleHorse
client configurations.

### Build jar file

Before running the containers, you need to have the Java artifact built, so, let's begin with that and
run the following command to build the UserTasks API jar file:

  ```shell
./gradlew bootJar
  ```

After successfully building the jar file, we are ready to start running our containers.

### Using Docker Compose

We are using Keycloak to work as a sample Identity Provider that will support UserTasks API by having a basic
identity provider configured. You can find a Docker Compose configuration
in `docker-compose.yaml`. You can run Keycloak, a local LH Server and the UserTasks API locally with this command:

  ```shell
  docker compose up -d
  ```

If the `docker compose up` command ended successfully, then you should see 4 containers running:

- 1 for Keycloak
- 1 for LittleHorse Standalone (LH Server and LH Dashboard)
- 1 for user-tasks-api
- 1 for user-tasks-ui

After that, the UserTasks API should be available on http://localhost:8089

Verify that Keycloak is up and running, and if that is the case, you can now access it through your browser
on http://localhost:8888

Keycloak's admin credentials: 
- username: **admin**
- password: **admin**

Also, you will need an access token to be able to be granted authorized access into user-tasks backend endpoints.
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

Once you have your access token handy, you are ready to hit user-tasks endpoints.

For example, you can hit the `/<tenant_id>/init` endpoint with the following cURL:

```shell
curl --request GET \
  --url http://localhost:8089/default/init \
  --header 'Authorization: Bearer replace-this-with-your-access-token'
```

### Other ways of running UserTasks API locally

#### Local LH Server

In order to run UserTasks API, you will need to have an LH Server running beforehand.
You can run an LH server locally by following the instructions
here: https://littlehorse.dev/docs/developer-guide/install

Once your LH server is up and running, then you can create a tenant to work with, and to do that you can run
the following command:

```shell
lhctl put tenant here-you-put-your-tenant-identifier
```

#### Set the following environment variables to let the LH Client within UserTasks API know where the LH Server is located

 ```shell
  export LHC_API_HOST=localhost
  export LHC_API_PORT=2023
  export LHC_TENANT_ID=here-you-put-your-tenant-identifier
  ```

This is just an example, use the values that match your LittleHorse config

#### Using source code

Make sure that you edit the `oidc-properties.yml` file located at `./ut-config/` directory.

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
- **clients**: Within this property you MUST set at least one client-id from your Keycloak realm from which your
  access tokens will be generated.

When you have your oidc-properties.yml properly configured, you will be ready to run UserTasks API from the source code.

This command will execute Spring Boot's run task:

  ```shell
  ./gradlew :bootRun
  ```

### Access Swagger UI

In order to see OpenAPI Specs with Swagger UI, and after having UserTasks API running, you just need to go to your
web browser and access the following URL: http://localhost:8089/swagger-ui/index.html
