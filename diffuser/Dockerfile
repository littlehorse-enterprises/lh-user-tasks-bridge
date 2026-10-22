FROM amazoncorretto:21

WORKDIR /workflow-diffuser
RUN mkdir -p /workflow-diffuser/bin

COPY ./build/install/lh-workflow-diffuser/lib /workflow-diffuser/lib
COPY --chmod=0755 ./build/install/lh-workflow-diffuser/bin/lh-workflow-diffuser /workflow-diffuser/bin/lh-workflow-diffuser

ENTRYPOINT ["/workflow-diffuser/bin/lh-workflow-diffuser"]
