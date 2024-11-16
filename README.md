# LH User Tasks

This repository contains the code for the UserTasks UI, and UserTasks API Client. This repository will help you interact with LittleHorse's UserTask API.

## Environment Variables

The following environment variables are required to run the UserTask UI:

- `NEXTAUTH_SECRET` Secret for next-auth
- `KEYCLOAK_HOST` Url for keycloak issuer
- `KEYCLOAK_REALM` Keycloak Realm
- `KEYCLOAK_CLIENT_ID` Keycloack client id
- `KEYCLOAK_CLIENT_SECRET` Keycloack client secret
- `LHUT_API_URL` User-Task proxy Url
- `LHUT_TENANT_ID` LH Tenant

## Prerequisites

Before starting the UserTask UI, ensure that the following services are running:

1. **LittleHorse Server**
2. **Keycloak Server**
3. **LH UserTasks API**

For detailed instructions on setting up these servers, please refer to the [LittleHorse documentation](https://github.com/littlehorse-enterprises/lh-user-tasks-api/blob/main/README.md).

## Getting Started

### Development

Install git hooks:

```shell
pre-commit install
```

Create a copy of `./ui/.env.sample` as `./ui/.env.local` and modify it accordingly to your littlehorse-server and keycloak configuration.

Then simply run

```shell
npm install
npm run dev -ws
```

The API Client will start listening to any live changes in the `api-client` folder and recompile it.

The UI will start with watch mode on [http://localhost:3000](http://localhost:3000)

### Start the UserTask UI with Docker

Build the docker image:

```sh
./local-dev/build-image.sh
```

```bash
docker run --name lh-user-tasks-ui -p 3000:3000 --rm -it \
  --env NEXTAUTH_URL='http://localhost:3000' \
  --env NEXTAUTH_SECRET=' ' \
  --env KEYCLOAK_HOST='http://keycloak:8888' \
  --env KEYCLOAK_REALM='default' \
  --env KEYCLOAK_CLIENT_ID='user-tasks-client' \
  --env KEYCLOAK_CLIENT_SECRET=' ' \
  --env LHUT_API_URL='http://localhost:8089' \
  --env LHUT_TENANT_ID='default' \
  littlehorse/lh-user-tasks-ui:latest
```
