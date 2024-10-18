# Standalone Image for Demos

## Useful commands

Run:

```shell
docker run --name lh-user-tasks-standalone -d \
        -p 2023:2023 \
        -p 8080:8080 \
        -p 8888:8888 \
        ghcr.io/littlehorse-enterprises/littlehorse/lh-user-tasks-standalone:latest
```

> Keycloak user: `admin`, password: `admin`

Local Build:

```shell
docker build -t littlehorse/lh-user-tasks-standalone:latest -f ./standalone/Dockerfile .
```

## Ports

| Service               | Port |
| --------------------- | ---- |
| LittleHorse           | 2023 |
| LittleHorse Dashboard | 8080 |
| Keycloak              | 8888 |
