# ADR 0003: Containerized Development Workflow

**Status:** Accepted

**Date:** 2026-08-15

## Context

The platform will contain independently runnable Java services and supporting infrastructure such
as PostgreSQL, Kafka, and, for a later justified use case, MongoDB.

Requiring every contributor to install and configure matching versions of Java, Maven, databases,
and message brokers would create environment drift. A change that works on one machine could then
fail on another machine or in continuous integration.

The first vertical slice already identifies Docker Compose as required local infrastructure. The
project therefore needs one documented container workflow before service implementation begins.

## Decision

### Primary contributor interface

Docker Compose will be the primary interface for building and running the complete local platform.

The main Compose file will be named:

```text
docker-compose.yml
```

and will be stored at the repository root. This supports a short, consistent contributor workflow:

```bash
docker compose build
docker compose up
docker compose down
```

Supporting initialization scripts and container-specific configuration will live under:

```text
infrastructure/docker/
```

### Docker and Maven responsibilities

Docker will provide the repeatable operating-system, Java, and runtime environment. Maven will
continue to compile, test, and package Java applications.

Service image builds will invoke the repository's Maven Wrapper rather than depend on Maven being
installed on the contributor's host machine.

```text
Docker build environment
└── Maven Wrapper
    ├── compile
    ├── test
    └── package
```

The standard container build must run the relevant automated tests. Build-cache improvements may
be added later, but they must not silently remove verification from the normal workflow.

### One image per service

Every independently deployable service will produce its own image. The services will not be
packaged into one shared application container.

```text
api-gateway image
identity-service image
escrow-service image
payment-service image
ledger-service image
```

Each image must be buildable independently even though Docker Compose can build and run the local
system as a group.

### Multi-stage service images

Service Dockerfiles will use multi-stage builds.

The build stage will contain the Java Development Kit, Maven Wrapper, and source required to
produce an executable artifact. The runtime stage will contain only the runtime dependencies and
the packaged application.

Runtime containers must:

- run as a non-root user;
- use an exec-form entry point so shutdown signals reach the Java process;
- avoid containing source code, Maven caches, or compilation tools;
- expose health information through the application's operational endpoints.

### Version policy

Container images must use explicit supported versions. Floating tags such as `latest` are not
allowed for application build images, databases, or message brokers.

Version changes must be deliberate, reviewed changes. Immutable image digests may be introduced
where stronger supply-chain reproducibility is required.

### Configuration and secrets

Runtime configuration will be supplied through environment variables or mounted configuration,
not baked into application images.

The repository may provide safe examples such as `.env.example`. It must not contain production
credentials, private keys, access tokens, or other real secrets.

Credentials used by `docker-compose.yml` must be clearly identified as local-development values
and must not be reused in shared or production environments.

### Container networking

Containers will address each other by Docker Compose service name and container port.

```text
identity-service:8080
postgres:5432
kafka:9092
mongo:27017
```

`localhost` inside a container refers to that same container and must not be used to reach another
Compose service.

Only ports required from the contributor's host will be published. The intended external entry
point is the API Gateway; temporary direct service ports used during development must be documented
and must not define the production network model.

Fixed `container_name` values will be avoided because Compose already provides service discovery
and fixed names make local replica scaling and project isolation harder.

### Persistent local data

Stateful infrastructure will use named Docker volumes so ordinary container recreation does not
delete development data.

Removing persistent volumes is a separate, deliberate operation. Project scripts must not delete
database or broker volumes as part of a normal shutdown.

### Health, readiness, and recovery

Stateful containers and application services will define meaningful health checks. Compose may use
health conditions to improve local startup ordering.

Startup ordering does not guarantee permanent availability. Applications must still be designed
for delayed dependencies, timeouts, lost connections, and restarted infrastructure. A `depends_on`
rule is not a replacement for retry limits, recovery behaviour, or observability.

### Incremental Compose scope

The Compose environment will grow with implemented use cases rather than starting with every
planned technology.

PostgreSQL is required for the initial transactional services. Kafka enters with the event-driven
funding workflow. MongoDB enters later with the document-oriented use case defined by its data
ownership ADR.

This keeps each build increment small while preserving one consistent contributor interface.

### Production relationship

Docker Compose is the local development and integration environment. It is not the production
orchestrator.

The same service images should be deployable by a later production platform such as Kubernetes,
but production networking, secrets, replicas, storage, and failure recovery will be designed
separately.

## Alternatives Considered

### Contributor-managed local installations

Installing Java, Maven, PostgreSQL, Kafka, and MongoDB directly on each workstation can provide a
fast inner development loop, but it makes version and configuration drift more likely. Direct local
tools may remain optional, but they are not the authoritative complete-platform environment.

### Compose file only under `infrastructure/docker/`

Keeping the primary Compose file below the infrastructure directory would group related assets,
but every normal command would require an additional file path. A root `docker-compose.yml` was
selected for contributor ergonomics, while supporting files remain under `infrastructure/docker/`.

### One container for all application services

One application container would be simpler initially, but it would prevent independent packaging,
scaling, and failure isolation. It would reproduce a monolithic deployment boundary and was
rejected.

### Single-stage service images

Building and running an application in the same tool-heavy image would be easier to write, but it
would create larger images with unnecessary compilers, build caches, and source files. Multi-stage
images were selected instead.

## Consequences

### Positive

- Contributors use consistent build and infrastructure versions.
- A clean checkout has one documented platform startup workflow.
- Services retain separate build and runtime boundaries.
- Runtime images are smaller and contain fewer unnecessary tools.
- The local topology teaches service networking and dependency failure explicitly.

### Negative

- Docker is required for the authoritative complete local environment.
- Initial image builds may be slower than direct host builds.
- Running several services and databases requires substantial memory and disk space.
- Debugging across host and container boundaries requires additional tooling and understanding.
- Compose cannot reproduce every behaviour of a production distributed deployment.

## Guardrails

```text
Use docker-compose.yml as the root local-platform entry point.

Use the Maven Wrapper inside service image builds.

Build one runtime image per independently deployable service.

Pin container versions; do not use latest.

Do not commit real secrets.

Do not use localhost for container-to-container communication.

Do not use fixed container names without a demonstrated requirement.

Do not delete persistent volumes during normal shutdown.

Do not describe Docker Compose as the production orchestrator.
```
