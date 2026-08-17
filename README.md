# Escrow Platform

A production-oriented, large-scale escrow platform built with **Java and the Spring ecosystem** to explore the engineering principles behind highly scalable distributed systems.

The platform connects buyers and sellers through a trusted escrow process: funds are secured until agreed transaction conditions are satisfied, after which they are released, refunded, or resolved through a dispute process.

> **Status:** Architecture complete enough for initial implementation. Development is starting with the first vertical slice.

---

## Run Locally with Docker Compose

Docker Compose is the primary interface for running the local platform consistently across contributor machines.

From the repository root:

```bash
docker compose build
docker compose up
```

When the Identity Service is healthy, verify it from another terminal:

```bash
curl http://localhost:8081/actuator/health
```

Expected response:

```json
{"status":"UP"}
```

Stop the platform without deleting persistent volumes:

```bash
docker compose down
```

The Compose environment currently contains only the Identity Service. Databases, brokers, and other services will be added when their first implemented use cases require them.

---

## Why This Project Exists

This project is intentionally more than a CRUD-based microservices application.

It is being built as a hands-on environment for learning how large backend systems are designed, implemented, scaled, observed, tested, and operated.

The target is to explore engineering problems that appear in high-scale systems such as:

* High request throughput
* Hundreds of millions of users
* Billions of database records
* Concurrent financial operations
* Distributed service communication
* Partial failures
* Event-driven processing
* Database scaling
* Fault tolerance
* Production observability

The long-term design target is:

```text
100M+ registered users
Millions of daily transactions
Large global traffic bursts
Up to 1M incoming requests/second under stress testing
```

These are architectural and learning targets, not claims about current production traffic.

---

## Product Overview

The platform provides general-purpose escrow between buyers and sellers.

A typical transaction follows:

```text
Buyer / Seller creates escrow
        ↓
Counterparty accepts terms
        ↓
Buyer funds escrow
        ↓
Payment is confirmed
        ↓
Funds are secured in the ledger
        ↓
Seller fulfils the agreement
        ↓
Buyer inspects delivery
        ↓
        ├── Accept → Release funds
        │
        └── Dispute → Resolution workflow
```

The platform is designed to eventually support:

* Physical goods
* Digital products
* Freelance services
* Professional services
* Peer-to-peer transactions
* Marketplace integrations
* B2B transactions
* Milestone-based transactions

---

## Core Engineering Goals

The project explores:

### Distributed Systems

* Service boundaries
* Partial failures
* Eventual consistency
* Idempotency
* Message duplication
* Message ordering
* Distributed workflows
* Sagas
* Transactional outbox
* Consumer inbox
* Backpressure
* Graceful degradation

### Event-Driven Architecture

**Kafka** is used for durable business events such as:

```text
PaymentSucceeded
EscrowFundingSecured
EscrowFunded
EscrowReleased
DisputeOpened
```

**RabbitMQ** is used for worker-oriented jobs such as:

```text
SendEmail
GenerateReceipt
ScanEvidence
GenerateStatement
```

General rule:

```text
Kafka
→ Something happened.

RabbitMQ
→ Some work needs to be performed.
```

---

## Database Engineering

PostgreSQL is the authoritative transactional database.

The project will cover:

* Database-per-service ownership
* ACID transactions
* Index design
* Query plans
* Optimistic locking
* Pessimistic locking
* Isolation levels
* Deadlocks
* Cursor pagination
* Connection pooling
* HikariCP
* PgBouncer
* Read replicas
* Table partitioning
* Data archival
* Sharding
* Large-table performance

Database scalability is treated as more than simply adding application replicas.

---

## Financial Ledger

Financial state is modelled using an immutable **double-entry ledger**.

Every financial journal must satisfy:

```text
Total Debits = Total Credits
```

Example release:

```text
Debit:
Escrow Held                  10,000

Credit:
Seller Available              9,800

Credit:
Platform Revenue                200
```

Important financial invariants include:

```text
No accidental creation of money.

No silent loss of money.

No duplicate funding.

No duplicate release.

No duplicate refund.

No duplicate payout.

No spending above available balance.

Every financial operation is auditable.
```

---

## Redis

Redis will be introduced for workloads such as:

* Distributed caching
* Rate limiting
* Fraud velocity counters
* Short-lived verification state
* Session-related state
* Idempotency acceleration
* Temporary coordination

Redis will **not** be the authoritative source of financial truth.

---

## Elasticsearch

Elasticsearch will provide derived search capabilities including:

* Transaction search
* User search
* Dispute investigation
* Full-text search
* Filtering
* Aggregations
* Administrative investigation

Data will typically flow through:

```text
PostgreSQL
    ↓
Transactional Outbox
    ↓
Kafka
    ↓
Search Indexer
    ↓
Elasticsearch
```

Elasticsearch is eventually consistent and will not make authoritative financial decisions.

---

## Planned Architecture

```text
                          Internet
                             |
                             v
                    CDN / Edge Security
                             |
                             v
                       Load Balancer
                             |
                             v
                        API Gateway
                             |
          +------------------+------------------+
          |                  |                  |
          v                  v                  v
     Identity           Escrow            Messaging
      Service           Service             Service
                             |
                   +---------+---------+
                   |                   |
                   v                   v
              Payment              Dispute
              Service              Service
                   |
                   v
                Ledger
                Service
                   |
                   v
                Payout
                Service
```

Asynchronous architecture:

```text
                         Kafka
                           |
             +-------------+-------------+
             |             |             |
             v             v             v
          Search          Risk          Audit
             |
             v
       Elasticsearch
```

Background work:

```text
                       RabbitMQ
                           |
              +------------+------------+
              |            |            |
           Email        Receipt       Evidence
           Worker        Worker        Worker
```

Performance:

```text
Redis Cluster
├── Cache
├── Rate Limits
└── Temporary / Velocity Data
```

---

## Initial Services

The first implementation will contain:

```text
api-gateway

identity-service

escrow-service

payment-service

ledger-service
```

Additional services will be introduced when their business or scaling requirements justify independent deployment.

Planned later services include:

```text
payout-service
dispute-service
risk-service
messaging-service
notification-service
search-indexer
reconciliation-service
```

---

## First Vertical Slice

The first distributed workflow being implemented is:

```text
Register User
      ↓
Create Escrow
      ↓
Accept Terms
      ↓
Initiate Funding
      ↓
Payment Confirmed
      ↓
PaymentSucceeded
      ↓ Kafka
Ledger Secures Funds
      ↓
EscrowFundingSecured
      ↓ Kafka
Escrow becomes FUNDED
```

This first slice will already include:

* PostgreSQL
* Flyway
* HikariCP
* UUIDv7
* Validation
* Idempotency
* Optimistic concurrency
* Kafka
* Transactional outbox
* Consumer deduplication
* Correlation IDs
* Structured logging
* Testcontainers
* Integration tests
* Concurrency tests

---

## Technology Stack

### Backend

* Java
* Spring Boot
* Spring Security
* Spring Data JPA
* Spring Kafka
* Spring AMQP
* Spring Cloud Gateway
* Resilience4j

### Data

* PostgreSQL
* Redis
* Elasticsearch

### Messaging

* Apache Kafka
* RabbitMQ

### Database Tooling

* Flyway
* HikariCP
* PgBouncer where justified

### Infrastructure

* Docker
* Docker Compose
* Kubernetes
* Terraform

### Observability

* OpenTelemetry
* Micrometer
* Prometheus
* Grafana
* Centralized structured logging

### Testing

* JUnit
* Mockito
* Testcontainers
* Toxiproxy
* k6 / Gatling

### CI/CD

* GitHub Actions

---

## Repository Structure

The repository will progressively evolve toward:

```text
escrow-platform/
│
├── docs/
│   ├── prd.md
│   ├── capacity-model.md
│   ├── invariants.md
│   ├── state-machine.md
│   ├── domain-model.md
│   │
│   └── architecture/
│       ├── high-level-architecture.md
│       ├── command-event-catalogue.md
│       ├── data-ledger-architecture.md
│       ├── api-design.md
│       ├── service-communication.md
│       ├── security.md
│       ├── reliability-observability.md
│       ├── testing-strategy.md
│       └── first-vertical-slice.md
│
├── adr/
│
├── services/
│   ├── api-gateway/
│   ├── identity-service/
│   ├── escrow-service/
│   ├── payment-service/
│   └── ledger-service/
│
├── contracts/
│   ├── api/
│   └── events/
│
├── infrastructure/
│   ├── docker/
│   ├── kubernetes/
│   ├── terraform/
│   └── observability/
│
├── load-tests/
│
└── .github/
    └── workflows/
```

Not every directory exists yet.

The structure will be created incrementally as implementation progresses.

---

## Architectural Principles

The platform follows several non-negotiable rules:

```text
One authoritative owner for important business data.

No direct cross-service database writes.

No financial truth stored only in Redis.

No financial decision based solely on Elasticsearch.

No financial command without idempotency.

No infinite retries.

No unbounded queues.

No remote calls without timeouts.

No assumption that messages are delivered only once.

No direct balance modification.

No long-running database transaction around remote calls.

No distributed-system design based on perfect network behaviour.
```

---

## Consistency Model

Not every operation requires the same consistency.

### Strong Consistency

Used for:

* Ledger posting
* Available balances
* Escrow state transitions
* Payout reservations
* Refund limits
* Idempotency records

### Eventual Consistency

Acceptable for:

* Search
* Notifications
* Analytics
* Dashboards
* Cached transaction views
* Risk projections

This distinction is a key part of the scalability model.

---

## Reliability

The platform assumes failure.

Examples:

```text
Kafka unavailable
→ event remains in transactional outbox

Redis unavailable
→ degraded performance, not financial corruption

Elasticsearch unavailable
→ search temporarily unavailable

Notification service unavailable
→ notifications delayed

Payment provider timeout
→ payment may become UNKNOWN

Ledger unavailable
→ financial operation fails safely
```

Unknown outcomes are reconciled rather than guessed.

---

## Observability

The system will provide:

* Correlation IDs
* Structured logs
* Distributed traces
* API latency metrics
* Database pool metrics
* Kafka consumer lag
* RabbitMQ queue depth
* Redis cache metrics
* Elasticsearch projection lag
* Financial business metrics

Critical metric:

```text
ledger imbalance count = 0
```

---

## Scalability Approach

The system will scale through measurement.

Load testing progresses approximately through:

```text
100 RPS
   ↓
1,000 RPS
   ↓
10,000 RPS
   ↓
100,000 RPS
   ↓
larger distributed tests
```

At each level, bottlenecks are measured before architecture changes are introduced.

The project deliberately avoids claiming scalability merely because microservices or Kubernetes are present.

---

## Git Workflow

The project uses trunk-based development with short-lived branches.

Typical workflow:

```text
main
  ↓
short-lived branch
  ↓
pull request
  ↓
automated checks
  ↓
review
  ↓
merge
```

Examples:

```text
feature/escrow-creation
feature/ledger-posting
fix/duplicate-release
infra/kafka-cluster
docs/capacity-model
```

Commit examples:

```text
feat: add escrow creation command

fix: prevent duplicate fund release

docs: define ledger architecture

test: add concurrent payout test

infra: add Kafka development cluster
```

---

## Architecture Decision Records

Important technical decisions are documented in:

```text
adr/
```

ADRs will explain decisions such as:

* Kafka and RabbitMQ responsibilities
* Database ownership
* Transactional outbox
* Ledger consistency
* REST versus gRPC
* Redis strategy
* Elasticsearch strategy
* Deployment decisions

The goal is to record not only **what** was chosen but **why**.

---

## Documentation

Important design documents are available under:

```text
docs/
```

Start with:

1. `prd.md`
2. `capacity-model.md`
3. `invariants.md`
4. `state-machine.md`
5. `domain-model.md`
6. `architecture/high-level-architecture.md`

The remaining architecture documents provide deeper implementation decisions.

---

## Current Project Status

### Completed

* Product requirements
* Capacity model
* System invariants
* Escrow lifecycle
* Domain boundaries
* High-level distributed architecture
* Command/event model
* Ledger architecture
* API conventions
* Service communication strategy
* Security model
* Reliability and observability strategy
* Testing strategy
* First vertical slice design

### Next

```text
Create Java/Spring service structure
        ↓
Create local infrastructure
        ↓
Build Identity Service
        ↓
Build Escrow Service
        ↓
Build Payment Service
        ↓
Build Ledger Service
        ↓
Connect the funding workflow with Kafka
```

---

## Learning Philosophy

This project does not add technology merely to increase the number of tools in the stack.

For every technology or pattern, the project should answer:

```text
What problem does it solve?

Why do we need it here?

What alternative exists?

What guarantees does it provide?

What does it not guarantee?

How does it fail?

How do we observe it?

How does it behave under load?

When should we avoid it?
```

The objective is not simply to know Spring Boot, Kafka, Redis, or Kubernetes.

The objective is to understand how to **engineer reliable backend systems at scale**.
