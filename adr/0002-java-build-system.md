# ADR 0002: Java Build System

**Status:** Accepted  
**Date:** 2026-08-15

## Context

The architecture selects Java and the Spring ecosystem, but it does not yet define the Java
version, build tool, or repository-level build structure.

The first vertical slice will contain several independently runnable services in one repository.
Contributors and continuous integration therefore need a repeatable build that prevents Java,
dependency, and plugin versions from drifting between services.

Local development will be containerized. The build tool is still required because Docker builds
must compile, test, and package each Java application before creating its runtime image.

## Decision

### Java version

Java 21 will be the project baseline.

Java 21 is a long-term-support release and provides a stable runtime target for the initial
implementation. All services must compile and run against the same Java release.

### Build tool

Apache Maven will compile, test, and package the Java services.

The repository will include the Maven Wrapper. The wrapper will pin the Maven version used by
contributors, container builds, and continuous integration, so a global Maven installation is not
required.

### Repository build structure

The repository will use a Maven multi-module structure with one root aggregator and parent build.

The root build may:

- list the service modules;
- align supported dependency and plugin versions;
- configure the Java release and common compiler settings;
- define common test and build quality rules.

The root build will not contain a deployable application or a shared business domain model.

Each service module must:

- produce its own executable application artifact;
- declare dependencies needed by that service;
- own its tests and runtime configuration;
- remain independently buildable;
- eventually produce its own Docker image.

### Shared-code boundary

Dependency management may be centralized, but domain entities, repositories, and business rules
must not be shared merely to reduce duplication.

Small shared technical libraries may be introduced later when a stable cross-cutting requirement
has been demonstrated. Their use must not prevent services from evolving or deploying
independently.

### Container relationship

The Maven Wrapper will be the canonical build entry point inside Docker builds and continuous
integration. Running it directly on a contributor's machine will remain optional.

The detailed Docker and Docker Compose development workflow will be recorded in a separate ADR.

## Alternatives Considered

### Gradle

Gradle is capable of building the platform and offers concise configuration and flexible build
logic. Maven was selected because its lifecycle and inherited configuration are explicit, widely
understood in the Spring ecosystem, and suitable for learning how dependency management works.

This choice is about build consistency and maintainability; it does not determine application
scalability.

### Independent build roots for every service

Giving every service a completely separate build would increase isolation, but it would duplicate
version and plugin configuration during the early stages of the project. The multi-module build
provides coordination while the service artifacts remain independent.

The services may move to separate repositories later if ownership, release cadence, or team scale
justifies that change.

### One deployable Maven module

A single module would resemble the monolithic architecture this project is intended to move
beyond. It would make independent packaging and service boundaries harder to enforce, so it was
rejected.

## Consequences

### Positive

- Contributors and CI use consistent Java, Maven, dependency, and plugin versions.
- All modules can be verified from one root command.
- Individual services can still be built and tested in isolation.
- Docker builds do not depend on Maven being installed on the host.
- Dependency upgrades can be coordinated and reviewed centrally.

### Negative

- The parent build creates some coordination between otherwise independent services.
- Maven XML is verbose.
- A full root build will take longer as more services are added.
- Care is required to prevent the multi-module repository from becoming a distributed monolith.

## Guardrails

```text
Centralize build policy, not business ownership.

Do not share JPA entities between services.

Do not introduce a shared library before a stable shared requirement exists.

Every service must remain independently packageable and containerizable.

Use the Maven Wrapper in local automation, Docker builds, and CI.
```
