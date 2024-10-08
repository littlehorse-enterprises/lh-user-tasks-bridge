# LH UserTask Ui

## Environment Variables

If running the app without Docker, you need to fill in the environment variables in the `.env.local` at the root folder. 
- `NEXTAUTH_SECRET` Cecret for next-auth
- `KEYCLOAK_HOST` Url for keycloak issuer
- `KEYCLOAK_REALM` Keycloak Realm
- `KEYCLOAK_CLIENT_ID` Keycloack client id
- `NEXT_PUBLIC_SERVER_URL`= user task proxy server url
- `NEXT_PUBLIC_KEYCLOAK_REALM` = issuer url


## Development

Create a copy of `.env-sample` as `.env-local` and modify it accordingly to your littlehorse-server and keycloak configuration.

Then simply run

```shell
npm install
npm run dev
```

The application will start with watch mode on [http://localhost:3000](http://localhost:3000)


## Start the UserTask Ui with Docker

2. Build the docker image

```sh
npm run build
docker build -t your-image-name .
```

```bash
docker run --rm \
  --env NEXTAUTH_SECRET='{secret}' \
  --env KEYCLOAK_HOST='{host url}' \
  --env KEYCLOAK_REALM='{realm name}' \
  --env KEYCLOAK_CLIENT_ID='{client-id}' \
  --env NEXT_PUBLIC_SERVER_URL='{NEXT_PUBLIC_SERVER_URL}'
  your-image-name

```