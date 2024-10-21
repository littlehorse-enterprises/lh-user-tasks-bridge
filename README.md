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

If the `docker compose up` command ended successfully, then you should see 3 containers running:

- 1 for Keycloak
- 1 for LittleHorse
- 1 for user-tasks backend.

After that, the UserTasks API should be available on http://localhost:8089

Verify that Keycloak is up and running, and if that is the case, you can now access it through your browser
on http://localhost:8888

- Login as admin (username: **admin**, password: **admin**)
- Switch to the **lh** realm
- Go to the **Clients** section, look for the client **user-tasks-client** in the list of clients and click on it
  to navigate to the **Settings** tab, scroll down until you reach the **Capability config** section, now disable the
  _**Client authentication**_ property (unless you want to use Service Account roles) and keep default values for all
  the other fields
- Within the **Clients** section, click on your client id to see the details. Once you are seeing your client's details
  navigate to the _Client Scopes_ tab and click on your client's scope. Once you have clicked on your client's scope,
  you
  will now see the mappers, and there you need to create one custom claim. To do that, click on _**Add mapper**_, and
  select
  _By configuration_. Now, from the list of mappings, choose **Hardcoded claim**. In the mapper details form you have to
  set
  **_allowed_tenant_** as the value in both fields _Name_ and _Token Claim Name_, and your tenant id MUST be the value
  of
  _Claim value_ field, keep the _Claim JSON Type_ as String and make sure that you switch on the _Add to access token_
  property
- Go to the **Users** section on the left sidebar, now you can create a user with username as `my-user`
  (email and names are optional, so, feel free to fill them up or ignore them)
- Go to the **Credentials** tab in **Users** section and add a password as `1234` (Make sure that you uncheck
  the `Temporary` field)
- Make sure that you assign the role `view-users` to all your users (ADMIN & NON-ADMIN) that will access UserTasks API
  so
  that they can query their own user details and see their groups. To do this you need to go to the **Users** section on
  the
  left sidebar, click on the user that you want to assign roles to, now go to the **_Role Mapping_** tab, click on
  `Assign role`,
  on the PopUp window that gets displayed select "Filter by clients", go through the list of available roles and select
  the one named as `view-users` and click on **Assign**
- In order to have an ADMIN user, you first need to create a role named as `lh-user-tasks-admin`. To create this role,
  you need to go to the **Realm roles** section on the left sidebar, click on `Create role`, set **_lh-user-tasks-admin_
  **
  as _Role name_ and click on `Save` (Description is optional, so feel free to ignore it if you want)
- Now that you have the UserTasks Admin role created, you can assign it to the users that you decide that are going to
  have ADMIN privileges.

Also, you will need an access token to be able to be granted authorized access into user-tasks backend endpoints.
In order to fetch said token, you can use the following cURL as is from the terminal, or you can import it
to your REST client tool of preference (Postman, Insomnia, Bruno, etc.)

```shell
curl --request POST \
--url http://localhost:8888/realms/lh/protocol/openid-connect/token \
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
  --url http://localhost:8089/lh/init \
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
