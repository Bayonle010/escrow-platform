FROM docker:29.8.0-cli AS docker-cli

FROM maven:3.9.16-eclipse-temurin-21-noble

COPY --from=docker-cli /usr/local/bin/docker /usr/local/bin/docker
COPY --from=docker-cli \
    /usr/local/libexec/docker/cli-plugins/docker-buildx \
    /usr/local/libexec/docker/cli-plugins/docker-buildx
COPY --from=docker-cli \
    /usr/local/libexec/docker/cli-plugins/docker-compose \
    /usr/local/libexec/docker/cli-plugins/docker-compose

WORKDIR /workspace

ENTRYPOINT ["mvn", "--batch-mode", "--no-transfer-progress"]
CMD ["-Psystem-tests", "-pl", "system-tests/funding-chain", "verify"]
