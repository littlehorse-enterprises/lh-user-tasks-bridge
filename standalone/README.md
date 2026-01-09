# Standalone Image for Demos

## Useful commands

Run:

```sh
docker run --name lh-user-tasks-bridge-standalone --rm -d --net=host ghcr.io/littlehorse-enterprises/lh-user-tasks-bridge-backend/lh-user-tasks-bridge-standalone:main
```

> Keycloak user: `admin`, password: `admin`

## Local build

Local Build:

```sh
./local-dev/build-images-backend.sh
```

Run:

```sh
docker run --name lh-user-tasks-bridge-standalone --rm -d --net=host littlehorse/lh-user-tasks-bridge-standalone:latest
```

Run a terminal:

```sh
docker run --rm -it --entrypoint="/bin/bash" littlehorse/lh-user-tasks-bridge-standalone:latest
```

## Ports

| Service                  | Port |
|--------------------------| ---- |
| LittleHorse Kernel       | 2023 |
| LittleHorse Dashboard    | 8080 |
| Keycloak                 | 8888 |
| User Task Bridge Backend | 8089 |
| User Task Bridge Console | 3000 |

## Users

Keycloak: <http://localhost:8888>

| User          | Pass  | Realm   |
| ------------- | ----- | ------- |
| my-user       | 1234  | default |
| my-admin-user | 1234  | default |
| admin         | admin | master  |
