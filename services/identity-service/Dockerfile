FROM maven:3.9.16-eclipse-temurin-21-noble AS build

WORKDIR /workspace

COPY pom.xml ./
COPY services/identity-service/pom.xml services/identity-service/pom.xml
COPY services/identity-service/src/ services/identity-service/src/

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --update-snapshots \
    -pl services/identity-service -am package -DskipTests

FROM eclipse-temurin:21.0.11_10-jre-noble AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=10001:10001 \
    /workspace/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar \
    /app/application.jar

USER 10001:10001

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
