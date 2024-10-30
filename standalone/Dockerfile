ARG LH_VERSION="0.11.2"
ARG KEYCLOAK_VERSION="26.0.1"

FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:${LH_VERSION}

ARG LH_VERSION
ARG KEYCLOAK_VERSION
ENV PATH=${PATH}:/keycloak/bin:/lhctl

RUN apt update && apt install -y jq openjdk-21-jdk python3-pip && apt clean
RUN python3 -m pip install --upgrade pip wheel && python3 -m pip install httpie

WORKDIR /keycloak
RUN wget -q https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz -O /tmp/keycloak.tar.gz \
    && tar -xzf /tmp/keycloak.tar.gz --strip-components 1 -C /keycloak \
    && rm /tmp/keycloak.tar.gz

WORKDIR /lhctl
RUN set -x; \
    dpkgArch="$(uname -m)" && \
    case "$dpkgArch" in \
    x86_64) ARCH='x86_64';; \
    aarch64) ARCH='arm64';; \
    *) echo >&2 "error: unsupported architecture: $dpkgArch"; exit 1 ;; \
    esac \
    && wget -q https://github.com/littlehorse-enterprises/littlehorse/releases/download/v${LH_VERSION}/lhctl_Linux_${ARCH}.tar.gz -O /tmp/lhctl.tar.gz \
    && tar -xzf /tmp/lhctl.tar.gz -C /lhctl \
    && rm /tmp/lhctl.tar.gz

WORKDIR /lh
COPY ./standalone/keycloak-entrypoint.sh /lh
COPY ./standalone/docker-entrypoint.sh /lh
COPY ./standalone/api-entrypoint.sh /lh
COPY ./standalone/ui-entrypoint.sh /lh
COPY ./local-dev/keycloak-configurer/configure-keycloak.sh /lh

WORKDIR /user-task-api
COPY ./api/build/libs/user-tasks.jar /user-task-api
COPY ./standalone/api-properties.yml /user-task-api

WORKDIR /user-task-ui
COPY ./.next/standalone /user-task-ui
COPY ./.next/static /user-task-ui/.next/static

WORKDIR /
