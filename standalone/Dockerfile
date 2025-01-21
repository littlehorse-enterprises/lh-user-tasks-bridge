FROM ghcr.io/littlehorse-enterprises/littlehorse/lh-standalone:master

ARG KEYCLOAK_VERSION="26.0.1"
ARG GRADLE_VERSION="8.12"
ENV PATH=${PATH}:/keycloak/bin

COPY --from=ghcr.io/littlehorse-enterprises/littlehorse/lhctl:latest /usr/local/bin/lhctl /usr/local/bin/lhctl

RUN apt update && apt install -y jq httpie && apt clean

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

WORKDIR /sso-workflow-bridge-api
COPY ./api/build/libs/sso-workflow-bridge-api.jar /sso-workflow-bridge-api
COPY ./standalone/api-properties.yml /sso-workflow-bridge-api

WORKDIR /sso-workflow-bridge-ui
COPY ./ui/.next/standalone /sso-workflow-bridge-ui
COPY ./ui/.next/static /sso-workflow-bridge-ui/ui/.next/static
COPY ./node_modules /sso-workflow-bridge-ui/node_modules

WORKDIR /
