# LH UserTask UI

## Environment Variables

If running the app without Docker, you need to fill in the environment variables in the `.env.local` at the root folder.

- `NEXTAUTH_SECRET` Secret for next-auth
- `KEYCLOAK_HOST` Url for keycloak issuer
- `KEYCLOAK_REALM` Keycloak Realm
- `KEYCLOAK_CLIENT_ID` Keycloack client id
- `KEYCLOAK_CLIENT_SECRET` Keycloack client secret
- `LHUT_API_URL` User-Task proxy Url
- `LHUT_TENANT_ID` LH Tenant

## Prerequisites

Before starting the UserTask UI, ensure that the following services are running:

1. **Littlehorse Server**
2. **Keycloak Server**
3. **LH UserTasks API**

For detailed instructions on setting up these servers, please refer to the [Littlehorse documentation](https://github.com/littlehorse-enterprises/lh-user-tasks-api/blob/main/README.md).

## Development

Install git hooks:

```shell
pre-commit install
```

Create a copy of `.env.sample` as `.env.local` and modify it accordingly to your littlehorse-server and keycloak configuration.

Then simply run

```shell
npm install
npm run dev
```

The application will start with watch mode on [http://localhost:3000](http://localhost:3000)

## Start the UserTask Ui with Docker

Build the docker image:

```sh
./local-dev/build-image.sh
```

```bash
docker run --rm \
  --env NEXTAUTH_URL='http://localhost:3000' \
  --env NEXTAUTH_SECRET='<any secret here>' \
  --env KEYCLOAK_HOST='http://keycloak:8888' \
  --env KEYCLOAK_REALM='default' \
  --env KEYCLOAK_CLIENT_ID='user-tasks-client' \
  --env KEYCLOAK_CLIENT_SECRET='<client secret>' \
  --env LHUT_API_URL='http://localhost:8089' \
  --env LHUT_TENANT_ID='default' \
  littlehorse/lh-user-tasks-ui:latest
```
