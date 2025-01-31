FROM amazoncorretto:21

RUN mkdir -p /lh/config

COPY ./entrypoint.sh /lh
COPY ./backend/build/libs/lh-user-tasks-bridge-backend.jar /lh

ENV LHUT_OIDC_CONFIG_FILE_LOCATION=/lh/config/oidc-properties.yml

EXPOSE 8089
ENTRYPOINT ["/lh/entrypoint.sh"]
