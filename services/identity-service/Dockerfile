FROM eclipse-temurin:21.0.11_10-jdk-noble AS build

WORKDIR /workspace

RUN apt-get update \
    && apt-get install --yes --no-install-recommends unzip \
    && rm -rf /var/lib/apt/lists/*

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/identity-service/pom.xml services/identity-service/pom.xml

RUN chmod +x mvnw \
    && ./mvnw --batch-mode --no-transfer-progress \
        -pl services/identity-service \
        -am \
        dependency:go-offline

COPY services/identity-service/src/ services/identity-service/src/

RUN ./mvnw --batch-mode --no-transfer-progress \
    -pl services/identity-service \
    -am \
    verify

FROM eclipse-temurin:21.0.11_10-jre-noble AS runtime

WORKDIR /app

RUN apt-get update \
    && apt-get install --yes --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd --gid 10001 app \
    && useradd --uid 10001 --gid app --no-create-home --shell /usr/sbin/nologin app

COPY --from=build --chown=10001:10001 \
    /workspace/services/identity-service/target/identity-service-0.1.0-SNAPSHOT.jar \
    /app/application.jar

USER 10001:10001

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "/app/application.jar"]
