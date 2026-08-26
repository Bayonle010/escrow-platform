FROM maven:3.9.16-eclipse-temurin-21-noble AS build

WORKDIR /workspace

COPY pom.xml ./
COPY services/payment-service/pom.xml services/payment-service/pom.xml
COPY services/payment-service/src/ services/payment-service/src/

RUN --mount=type=cache,target=/root/.m2 \
    mvn --batch-mode --no-transfer-progress --update-snapshots \
    --file services/payment-service/pom.xml package -DskipTests

FROM eclipse-temurin:21.0.11_10-jre-noble AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/*

COPY --from=build --chown=10001:10001 \
    /workspace/services/payment-service/target/payment-service-0.1.0-SNAPSHOT.jar \
    /app/application.jar

USER 10001:10001

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
