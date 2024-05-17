# user-tasks

Backend component that serves as a proxy between User Tasks' UI component and LittleHorse Server.

## Getting Started

### Software requirements

The following software packages are needed to be installed:

- [Brew](https://brew.sh/)
- [Docker](https://www.docker.com/)
- [Java 21](https://www.oracle.com/mx/java/technologies/downloads/)

Run the following commands to install pre-commit
`brew install pre-commit`

### Set the following environment variables to let user-tasks service know where the LH Server is located

 ```shell
  export LHC_API_HOST=localhost
  export LHC_API_PORT=2025
  ```

This is just an example, use the values that match your LittleHorse config

### Running user-tasks service locally

First, you need to run Keycloak.

#### Local Keycloak

We are using Keycloak to work as a sample OIDC provider that will support user-tasks service by having a basic
identity provider configured. You can find user-tasks-keycloak Docker Compose configuration
in `docker-compose.yaml`. You can run Keycloak locally with this command:

  ```shell
  docker compose up -d
  ```

Once Keycloak is up and running, you can access it through your browser on http://localhost:8888

- Login as admin (username: **admin**, password: **admin**)
- Switch to the **lh** realm
- Go to the **Clients** section and create a client with *client-id* as `user-tasks-test-client ` and keep default
  values for the other fields
- Now, go to the **Users** section and add a user with username as `user-tasks-test-user`
- Go to the **Credentials** tab in **Users** section and add a password as `1234` (Make sure that you uncheck
  the `Temporary` field)

Now, you will need an access token to be able to be granted authorized access into user-tasks endpoints.
In order to fetch said token, you can use the following cURL as is from the terminal or you can import it
to your REST client tool of preference (Postman, Insomnia, Bruno, etc)

```shell
curl --request POST \
--url http://localhost:8888/realms/lh/protocol/openid-connect/token \
--header 'Content-Type: application/x-www-form-urlencoded' \
--data client_id=user-tasks-test-client \
--data username=user-tasks-test-user \
--data password=1234 \
--data grant_type=password
```

#### Local User Tasks

This command will execute Spring Boot's run task:

  ```shell
  ./gradlew :bootRun
  ```

After that, user-tasks service will be available on http://localhost:8089

Once you have your access token handy, you are ready to hit user-tasks endpoints.

For example, you can hit the `/<tenantId>/init` endpoint with the following cURL:

```shell
curl --request GET \
  --url http://localhost:8089/lh/init \
  --header 'Authorization: Bearer replace-this-with-your-access-token'
```
