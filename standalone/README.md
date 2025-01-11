# Standalone Image for Demos

## Useful commands

Run:

```shell
docker run --name lh-sso-workflow-bridge-standalone --rm -d\
        -p 2023:2023 \
        -p 8080:8080 \
        -p 8888:8888 \
        -p 8089:8089 \
        -p 3000:3000 \
        ghcr.io/littlehorse-enterprises/lh-sso-workflow-bridge-api/lh-sso-workflow-bridge-standalone:main
```

> Keycloak user: `admin`, password: `admin`

## Demo

GO to [demo](../demo/README.md)

## Local build

Local Build:

```shell
./local-dev/build-images.sh
```

Run:

```shell
docker run --name lh-sso-workflow-bridge-standalone --rm -d \
        -p 2023:2023 \
        -p 8080:8080 \
        -p 8888:8888 \
        -p 8089:8089 \
        -p 3000:3000 \
        littlehorse/lh-sso-workflow-bridge-standalone:latest
```

Run a terminal:

```shell
docker run --rm -it --entrypoint="/bin/bash" littlehorse/lh-sso-workflow-bridge-standalone:latest
```

## Ports

| Service               | Port |
| --------------------- | ---- |
| LittleHorse           | 2023 |
| LittleHorse Dashboard | 8080 |
| Keycloak              | 8888 |
| User Task API         | 8089 |
| User Task UI          | 3000 |

## Users

Keycloak: <http://localhost/8888>

| User          | Pass  | Realm   |
| ------------- | ----- | ------- |
| my-user       | 1234  | default |
| my-admin-user | 1234  | default |
| admin         | admin | master  |
