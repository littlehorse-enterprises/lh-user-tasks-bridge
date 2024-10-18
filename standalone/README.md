# Standalone Image for Demos

## Useful commands

Run:

```shell
docker run --name lh-user-tasks-standalone -d \
        -p 2023:2023 \
        -p 8080:8080 \
        -p 8888:8888 \
        -p 8089:8089 \
        ghcr.io/littlehorse-enterprises/littlehorse/lh-user-tasks-standalone:latest
```

> Keycloak user: `admin`, password: `admin`

Local Build:

```shell
./gradlew build
docker build -t ghcr.io/littlehorse-enterprises/littlehorse/lh-user-tasks-standalone:latest -f ./standalone/Dockerfile .
```

Run terminal:

```shell
docker run --rm -it --entrypoint="/bin/bash" ghcr.io/littlehorse-enterprises/littlehorse/lh-user-tasks-standalone:latest
```

## Ports

| Service               | Port |
| --------------------- | ---- |
| LittleHorse           | 2023 |
| LittleHorse Dashboard | 8080 |
| Keycloak              | 8888 |
| User Task API         | 8089 |
