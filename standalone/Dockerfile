FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:latest

ARG KEYCLOAK_VERSION="26.0.1"
ARG GRADLE_VERSION="8.12"
ENV PATH=${PATH}:/keycloak/bin

COPY --from=ghcr.io/littlehorse-enterprises/littlehorse/lhctl:latest /usr/local/bin/lhctl /usr/local/bin/lhctl

RUN apt update && apt install -y jq httpie unzip && apt clean

WORKDIR /keycloak
RUN wget -q https://github.com/keycloak/keycloak/releases/download/${KEYCLOAK_VERSION}/keycloak-${KEYCLOAK_VERSION}.tar.gz -O /tmp/keycloak.tar.gz \
    && tar -xzf /tmp/keycloak.tar.gz --strip-components 1 -C /keycloak \
    && rm /tmp/keycloak.tar.gz

WORKDIR /gradle
RUN wget -q https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip -O /tmp/gradle.zip \
    && unzip -q /tmp/gradle.zip \
    && mv gradle-${GRADLE_VERSION}/* . \
    && rm /tmp/gradle.zip \
    && rm -rf gradle-${GRADLE_VERSION} \
    && ln -s /gradle/bin/gradle /usr/local/bin/gradle

WORKDIR /lh
COPY ./standalone/keycloak-entrypoint.sh /lh
COPY ./standalone/docker-entrypoint.sh /lh
COPY ./standalone/backend-entrypoint.sh /lh
COPY ./standalone/console-entrypoint.sh /lh
COPY ./local-dev/keycloak-configurer/configure-keycloak.sh /lh

WORKDIR /lh-user-tasks-bridge-backend
COPY ./backend/build/libs/lh-user-tasks-bridge-backend.jar /lh-user-tasks-bridge-backend
COPY ./standalone/backend-properties.yml /lh-user-tasks-bridge-backend

WORKDIR /lh-user-tasks-bridge-console
COPY ./console/.next/standalone /lh-user-tasks-bridge-console
COPY ./console/.next/static /lh-user-tasks-bridge-console/console/.next/static
COPY ./node_modules /lh-user-tasks-bridge-console/node_modules

# Configure LittleHorse with Demo Workflow
COPY ./demo-workflow/build/libs/lh-user-tasks-bridge-demo-all.jar /lh/build/libs
COPY ./demo-workflow/configure-littlehorse.sh /lh
RUN chmod +x /lh/configure-littlehorse.sh

WORKDIR /
