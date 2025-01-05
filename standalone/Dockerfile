FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:latest

ARG LH_VERSION="0.12.1"
ARG KEYCLOAK_VERSION="26.0.1"
ARG GRADLE_VERSION="8.12"
ENV PATH=${PATH}:/keycloak/bin

COPY --from=ghcr.io/littlehorse-enterprises/littlehorse/lhctl:latest /usr/local/bin/lhctl /usr/local/bin/lhctl

RUN apt update && apt install -y jq openjdk-21-jdk python3-pip unzip && apt clean
RUN python3 -m pip install --upgrade pip wheel && python3 -m pip install httpie

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
COPY ./standalone/api-entrypoint.sh /lh
COPY ./standalone/ui-entrypoint.sh /lh
COPY ./local-dev/keycloak-configurer/configure-keycloak.sh /lh
# Configure LittleHorse with Demo Workflow
COPY ./local-dev/littlehorse-configurer /lh
RUN chmod +x /lh/configure-littlehorse.sh

WORKDIR /user-task-api
COPY ./api/build/libs/user-tasks.jar /user-task-api
COPY ./standalone/api-properties.yml /user-task-api

WORKDIR /user-task-ui
COPY ./ui/.next/standalone /user-task-ui
COPY ./ui/.next/static /user-task-ui/ui/.next/static
COPY ./node_modules /user-task-ui/node_modules

WORKDIR /
