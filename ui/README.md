# LH UserTask UI

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

## Start the UserTask UI with Docker

Build the docker image:

```sh
./local-dev/build-image.sh
```

```bash
docker run --name lh-user-tasks-ui -p 3000:3000 --rm \
  --env NEXTAUTH_URL='http://localhost:3000' \
  --env NEXTAUTH_SECRET='<any secret here>' \
  --env KEYCLOAK_HOST='http://keycloak:8888' \
  --env KEYCLOAK_REALM='default' \
  --env KEYCLOAK_CLIENT_ID='user-tasks-client' \
  --env KEYCLOAK_CLIENT_SECRET=' ' \
  --env LHUT_API_URL='http://localhost:8089' \
  littlehorse/lh-user-tasks-ui:latest
```
