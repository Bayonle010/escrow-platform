# Escrow Platform High-Level Architecture

**Version:** 1.0
**Status:** Draft
**Target scale:** 100 million registered users
**Peak stress objective:** 1 million incoming requests per second

---

# 1. Purpose

This document defines the first high-level technical architecture for the escrow platform.

The architecture is designed around the following principles:

* Financial correctness before availability.
* Clear data ownership.
* Horizontal scalability.
* Local database transactions.
* Event-driven integration.
* Failure isolation.
* Idempotent processing.
* Backpressure.
* Observability.
* Independent scaling of different workloads.

This document does not attempt to define every class, table, Kafka topic, or Kubernetes deployment.

Those will be designed later.

---

# 2. High-Level Architecture

```text
                           Internet
                              |
                              v
                     DNS / Global Traffic
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
          +-------------------+-------------------+
          |                   |                   |
          v                   v                   v
   Identity Service     Escrow Service     Messaging Service
                              |
                 +------------+------------+
                 |                         |
                 v                         v
          Payment Service            Dispute Service
                 |
                 v
           Ledger Service
                 |
                 v
           Payout Service
```

Supporting asynchronous systems:

```text
                         Kafka Cluster
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
   Search Indexer          Risk Engine          Audit Pipeline
        |                     |                     |
        v                     v                     v
 Elasticsearch          Risk Storage          Audit Storage
```

Background work:

```text
                         RabbitMQ Cluster
                              |
        +---------------------+---------------------+
        |                     |                     |
        v                     v                     v
 Email Workers          Receipt Workers       Evidence Workers
```

Performance layer:

```text
                       Redis Cluster
                           |
             +-------------+-------------+
             |             |             |
          Cache       Rate Limits     Velocity Data
```

Authoritative transactional storage:

```text
Identity DB
Escrow DB
Payment DB
Ledger DB
Payout DB
Dispute DB
```

Large binary files:

```text
Object Storage
```

Observability:

```text
OpenTelemetry
     |
     +---- Metrics ----> Prometheus ----> Grafana
     |
     +---- Traces -----> Trace Backend
     |
     +---- Logs -------> Central Log Platform
```

---

# 3. Traffic Entry Layer

Requests from clients must not directly reach individual services.

Traffic enters through:

```text
Client
   ↓
DNS
   ↓
CDN / Edge
   ↓
Load Balancer
   ↓
API Gateway
```

Possible clients include:

* Web applications
* Mobile applications
* Marketplace integrations
* Internal administrative tools
* Partner APIs
* Webhook providers

---

# 4. DNS and Global Traffic Routing

At large scale, DNS or global traffic management determines which regional platform endpoint receives a request.

Future architecture may support:

```text
User in Nigeria
     ↓
Africa Region

User in Germany
     ↓
Europe Region

User in United States
     ↓
North America Region
```

Routing decisions may consider:

* Geographic proximity
* Region availability
* Data residency
* Service health
* Latency
* Regulatory requirements

We will begin with one region but preserve this architectural boundary.

---

# 5. CDN and Edge Layer

The edge layer handles work that should not reach application servers unnecessarily.

Responsibilities may include:

* TLS termination
* Static asset delivery
* DDoS protection
* Bot detection
* Web application firewall
* IP reputation
* Geographic filtering
* Request-size enforcement
* Basic rate limiting
* Caching public resources

Example:

```text
1,000,000 incoming requests/sec
            ↓
        Edge Layer
            ↓
650,000 forwarded requests/sec
```

The exact numbers will come from load testing.

---

# 6. API Gateway

The API Gateway is the primary application entry point.

Responsibilities include:

* Routing
* Authentication integration
* Authorization context propagation
* Rate limiting
* Request correlation IDs
* API version routing
* Header sanitisation
* Request size restrictions
* Client-specific quotas
* Observability
* Response aggregation where justified

Potential implementation:

```text
Spring Cloud Gateway
```

The gateway must remain mostly stateless.

---

# 7. What the API Gateway Must Not Do

The gateway must not become the business logic layer.

It should not decide:

```text
Can this escrow be released?
```

That belongs to the Escrow domain.

It should not decide:

```text
Does the seller have enough available money?
```

That belongs to the Ledger domain.

It should not contain provider-specific payment logic.

The gateway handles traffic management, not domain decisions.

---

# 8. Authentication Flow

Example:

```text
Client
   ↓
API Gateway
   ↓
Identity Service
```

The Identity Service may issue tokens containing:

* User ID
* Session ID
* Roles
* Token expiry
* Authentication strength

Subsequent requests may be validated locally using signed JWTs where appropriate.

Example:

```text
Client sends JWT
     ↓
Gateway validates signature
     ↓
userId propagated to downstream service
```

Downstream services must still perform resource-level authorization.

---

# 9. Escrow Service

The Escrow Service owns:

* Escrow creation
* Buyer/seller relationship
* Escrow terms
* Terms versions
* Terms acceptance
* Lifecycle state
* Delivery status
* Inspection rules
* Release eligibility
* Refund eligibility

Primary database:

```text
PostgreSQL
```

The Escrow Service is the authoritative owner of:

```text
escrow state
```

---

# 10. Payment Service

The Payment Service handles inbound external money.

Responsibilities:

* Funding instructions
* Provider integrations
* Payment attempts
* Provider callbacks
* Payment verification
* Unknown provider outcomes
* Provider-specific idempotency
* Payment reconciliation

Database:

```text
PostgreSQL
```

Provider integrations are hidden behind adapters.

Conceptually:

```text
Payment Domain
     |
     +---- Stripe Adapter
     |
     +---- Adyen Adapter
     |
     +---- Bank Transfer Adapter
     |
     +---- Regional Provider Adapter
```

The domain should not depend directly on provider-specific response models.

---

# 11. Ledger Service

The Ledger Service is one of the most critical services.

Responsibilities:

* Ledger accounts
* Financial journals
* Double-entry posting
* Reservations
* Holds
* Releases
* Refund entries
* Reversals
* Balance projections
* Financial adjustments

Database:

```text
PostgreSQL
```

Ledger storage should eventually receive dedicated infrastructure because its workload, security requirements, and consistency guarantees differ from ordinary application data.

---

# 12. Ledger Isolation

Only controlled APIs or commands may interact with the ledger.

No service may directly manipulate ledger tables.

Bad:

```text
Escrow Service
    ↓
ledger database
```

Good:

```text
Escrow Service
    ↓ command/event
Ledger Service
    ↓
Ledger Database
```

This produces:

* Clear ownership
* Auditability
* Security isolation
* Easier reconciliation
* Independent scaling

---

# 13. Payout Service

The Payout Service manages money leaving the platform.

Responsibilities:

* Seller payout requests
* Payout destinations
* Provider routing
* Fund reservation
* Provider payout execution
* Retry
* Unknown payout outcomes
* Payout reconciliation

Payment and Payout are separate domains.

```text
Payment:
External → Platform

Payout:
Platform → External
```

---

# 14. Dispute Service

The Dispute Service manages disagreement between counterparties.

Responsibilities:

* Case creation
* Evidence
* Responses
* Officer assignment
* Resolution
* Appeals
* Split resolutions
* Escalation

Its decisions may produce commands or events that affect Escrow and Ledger.

Example:

```text
DisputeResolvedForBuyer
       ↓
Escrow Service
       ↓
REFUND_PENDING
       ↓
Ledger Service
```

---

# 15. Risk and Compliance Service

Risk processing consumes activity from across the platform.

Examples:

```text
UserRegistered
EscrowCreated
PaymentSucceeded
DisputeOpened
PayoutRequested
PayoutDestinationChanged
```

The service evaluates:

* Fraud risk
* Transaction velocity
* Account relationships
* Device risk
* Behaviour patterns
* Compliance rules
* Transaction limits

Many risk workloads are naturally event-driven.

Kafka is therefore important here.

---

# 16. Messaging Service

Messaging is separated because its workload differs greatly from financial operations.

Responsibilities:

* Buyer-seller chat
* Real-time communication
* Read receipts
* Message history
* Attachments
* Moderation signals
* System messages

Messaging traffic may eventually exceed escrow transaction traffic by a large factor.

Its scaling must not compete with ledger processing.

---

# 17. Notification Service

Notification responsibilities include:

* Email
* SMS
* Push notifications
* In-app notifications
* Templates
* Preferences
* Delivery tracking

Notifications are asynchronous.

For example:

```text
EscrowFunded
    ↓ Kafka
Notification Service
    ↓
RabbitMQ
    ↓
Email Worker
```

If email delivery fails:

```text
Escrow remains FUNDED
```

The financial operation must not roll back.

---

# 18. Search Architecture

Users and administrators require advanced search.

Search queries should not overload transactional PostgreSQL databases.

Architecture:

```text
Escrow Service
     ↓
Transactional Outbox
     ↓
Kafka
     ↓
Search Indexer
     ↓
Elasticsearch
```

Elasticsearch stores projections.

Example document:

```text
EscrowSearchDocument
├── escrowId
├── buyerId
├── sellerId
├── amount
├── currency
├── state
├── category
├── createdAt
└── searchableText
```

Elasticsearch is eventually consistent.

---

# 19. Redis Architecture

Redis will initially provide several capabilities.

## Caching

Example:

```text
GET /escrows/{id}
       ↓
Escrow Service
       ↓
Redis
  ├── hit → return
  └── miss
       ↓
PostgreSQL
       ↓
cache result
```

## Rate limiting

Example:

```text
rate-limit:user:123
```

## Fraud velocity

Example:

```text
payments:user:123:last-5-minutes
```

## Temporary state

Examples:

* OTPs
* Short-lived sessions
* Temporary tokens

Redis must not become the authoritative financial database.

---

# 20. Kafka Architecture

Kafka is the primary durable event backbone.

Conceptually:

```text
Service
   ↓
Local DB transaction
   ↓
Transactional Outbox
   ↓
Outbox Publisher
   ↓
Kafka
   ↓
Consumer Groups
```

Example event:

```text
EscrowReleased
```

Consumers may include:

```text
Search Consumer Group

Notification Consumer Group

Risk Consumer Group

Audit Consumer Group

Analytics Consumer Group
```

Each consumer group processes the event independently.

---

# 21. Why Kafka Instead of Direct Service Calls

Without Kafka:

```text
Escrow Service
    |
    +--> Notification Service
    |
    +--> Search Service
    |
    +--> Risk Service
    |
    +--> Audit Service
    |
    +--> Analytics Service
```

If one dependency becomes slow, Escrow Service may become slow.

With Kafka:

```text
Escrow Service
      ↓
EscrowReleased
      ↓
Kafka
      |
      +--> Notification
      +--> Search
      +--> Risk
      +--> Audit
      +--> Analytics
```

The Escrow Service no longer waits for these consumers.

---

# 22. Kafka Does Not Replace All APIs

Kafka should not be used blindly.

Suppose Escrow Service needs an immediate answer:

```text
Is seller currently allowed to receive a payout?
```

If the decision requires fresh authoritative information, synchronous communication may be more appropriate.

Therefore:

```text
HTTP/gRPC
```

and:

```text
Kafka
```

will coexist.

---

# 23. RabbitMQ Architecture

RabbitMQ handles worker-oriented tasks.

Architecture:

```text
Producer
   ↓
Exchange
   ↓
Queue
   ↓
Worker Pool
```

Example:

```text
SendReceipt
   ↓
receipt.exchange
   ↓
receipt.generate.queue
   ↓
Receipt Worker
```

Workers compete for jobs.

Typically, one worker performs one job.

---

# 24. Kafka Versus RabbitMQ

Kafka:

```text
A business fact happened.
Multiple independent consumers may care.
History/replay is useful.
```

RabbitMQ:

```text
This work needs to be done.
One worker should normally perform it.
```

Example:

```text
EscrowReleased
```

belongs naturally in Kafka.

Example:

```text
GenerateEscrowReceipt
```

belongs naturally in RabbitMQ.

---

# 25. Transactional Outbox

A core pattern in the platform is the transactional outbox.

Without it:

```text
BEGIN

UPDATE escrow

COMMIT

publish Kafka event
```

A crash after the database commit but before Kafka publishing produces:

```text
Database changed
Kafka event missing
```

Instead:

```text
BEGIN

UPDATE escrow

INSERT outbox_event

COMMIT
```

Then:

```text
Outbox Publisher
      ↓
Kafka
```

This means the business change and event intent are persisted atomically.

---

# 26. Outbox Publication

Possible initial design:

```text
Application Transaction
       ↓
outbox_events table
       ↓
background publisher
       ↓
Kafka
       ↓
mark published
```

Later, we may compare this with Change Data Capture using:

```text
Debezium
```

That comparison will be a separate architecture decision.

---

# 27. Consumer Inbox Pattern

Consumers must assume duplicate delivery.

Example:

```text
eventId = abc-123
```

Inbox table:

```text
consumer_name
event_id
processed_at
```

Unique constraint:

```text
UNIQUE(consumer_name, event_id)
```

Processing conceptually becomes:

```text
BEGIN

Check inbox

If already processed:
    return

Apply business change

Insert inbox record

COMMIT
```

This helps provide idempotent business effects.

---

# 28. Database Architecture

Each authoritative domain owns its data.

Logical ownership:

```text
identity_db
escrow_db
payment_db
ledger_db
payout_db
dispute_db
```

During development these may run in:

```text
one PostgreSQL server
```

but use separate databases or schemas.

Later production topology may use independent clusters.

---

# 29. Why We Do Not Start With One Database Per Service Server

If we immediately deploy:

```text
10 PostgreSQL clusters
Kafka cluster
RabbitMQ cluster
Redis cluster
Elasticsearch cluster
```

on a development laptop, we learn infrastructure pain rather than architecture.

Instead:

```text
Logical separation first
Physical separation as scale and reliability require
```

This preserves ownership without unnecessary early cost.

---

# 30. Read Replicas

Read-heavy services may eventually use replicas.

Example:

```text
Primary
   |
   +--> Replica 1
   +--> Replica 2
   +--> Replica 3
```

Writes go to the primary.

Eligible reads may use replicas.

But replica lag introduces eventual consistency.

Therefore critical decisions such as:

```text
Can this money be released?
```

must not depend on stale replica data.

---

# 31. Database Partitioning

Large tables will eventually require partitioning.

Candidates include:

```text
ledger_entries
escrows
payments
audit_events
messages
outbox_events
```

Potential strategies:

```text
Hash partition by aggregate ID

Range partition by time

Composite partitioning
```

Partitioning will be designed after examining query patterns.

We must not partition purely because a table is large.

---

# 32. Database Connection Management

Every service instance uses a bounded connection pool.

Example:

```text
50 escrow-service replicas
× 8 connections
=
400 potential DB connections
```

This means service scaling and database scaling are connected.

We must monitor:

* Active connections
* Idle connections
* Waiting requests
* Connection acquisition time
* Query duration
* Transaction duration

HikariCP will be used in Spring applications.

PgBouncer may later be introduced.

---

# 33. Connection Pool Saturation

Suppose all connections are busy:

```text
Request
   ↓
Service
   ↓
HikariCP
   ↓
NO CONNECTION AVAILABLE
```

The service must not create unlimited new database connections.

Instead it may:

* Wait within a bounded timeout.
* Apply backpressure.
* Return an error.
* Shed non-critical load.

Unbounded connection creation can destroy the database.

---

# 34. Object Storage

Evidence files should not be stored directly in PostgreSQL.

Architecture:

```text
Client
   ↓
Request upload permission
   ↓
Evidence Service
   ↓
Signed upload URL
   ↓
Client
   ↓
Object Storage
```

Then:

```text
ObjectUploaded
    ↓
RabbitMQ
    ↓
Malware Scan Worker
```

Metadata remains in PostgreSQL.

---

# 35. Service-to-Service Communication

Three major communication patterns will exist.

## Synchronous

```text
HTTP REST
```

or later:

```text
gRPC
```

Used when an immediate answer is required.

## Durable event stream

```text
Kafka
```

Used for business facts.

## Work queue

```text
RabbitMQ
```

Used for background tasks.

Choosing between them is an architectural decision, not a framework preference.

---

# 36. Timeouts

Every remote synchronous request must have a timeout.

Bad:

```text
wait forever
```

Good:

```text
connect timeout
request timeout
total operation deadline
```

A service must assume downstream services can become slow.

---

# 37. Retries

Retries are allowed only when the operation is safe.

Retry strategy should include:

```text
Maximum attempts
Exponential backoff
Jitter
Idempotency
```

Example:

```text
100 ms
250 ms
600 ms
```

rather than:

```text
retry immediately forever
```

---

# 38. Circuit Breakers

If Payment Provider A fails repeatedly:

```text
Payment Service
      ↓
Circuit Breaker
      X
Provider A
```

The system temporarily stops sending traffic to the failing provider.

This protects:

* Threads
* Connections
* Provider capacity
* Internal system stability

Potential library:

```text
Resilience4j
```

---

# 39. Bulkheads

Failure in one external dependency should not consume all resources.

Example:

```text
Payment Provider A thread pool
Payment Provider B thread pool
Notification thread pool
```

If Provider A becomes slow, it should not exhaust resources needed for unrelated operations.

---

# 40. Backpressure

Every high-volume pipeline needs a way to slow producers or reject work.

Examples:

```text
API → rate limits

Service → bounded thread pool

Service → bounded DB connections

Kafka → consumer lag monitoring

RabbitMQ → queue depth

Elasticsearch → rejected request monitoring
```

Unlimited queues are delayed outages.

---

# 41. Service Discovery

Inside Kubernetes, services can discover each other using Kubernetes DNS.

Example:

```text
http://ledger-service
```

Therefore we may not need something like Eureka in a Kubernetes deployment.

This is an important lesson:

```text
Do not add service discovery technology
when the deployment platform already provides it.
```

---

# 42. Kubernetes Deployment

Eventually:

```text
Kubernetes Cluster
```

contains:

```text
api-gateway
identity-service
escrow-service
payment-service
ledger-service
payout-service
dispute-service
notification-service
search-indexer
```

Each service may have:

* Deployment
* Service
* ConfigMap
* Secret references
* Horizontal Pod Autoscaler
* Pod disruption policy

---

# 43. Horizontal Scaling

Example:

```text
Escrow Service

Pod 1
Pod 2
Pod 3
...
Pod 50
```

Requests are load-balanced across replicas.

Therefore services should avoid storing essential state only in application memory.

---

# 44. Autoscaling Signals

CPU is not always the best scaling signal.

Possible signals include:

```text
CPU utilisation

Request latency

Requests per second

Kafka consumer lag

RabbitMQ queue depth

Active WebSocket connections
```

Different services may require different autoscaling metrics.

---

# 45. Observability Architecture

Every request receives a correlation ID.

Example:

```text
POST /escrows/123/release
correlationId = 92ab...
```

That ID flows through:

```text
API Gateway
     ↓
Escrow Service
     ↓
Kafka
     ↓
Ledger Service
     ↓
Kafka
     ↓
Notification Service
```

This allows an engineer to reconstruct the complete workflow.

---

# 46. Metrics

Prometheus will collect metrics such as:

```text
http_request_duration

http_request_count

database_connection_active

database_connection_waiting

kafka_consumer_lag

rabbitmq_queue_depth

redis_cache_hit_ratio

ledger_posting_latency

escrows_created_total

funds_released_total
```

Grafana will visualise them.

---

# 47. Distributed Tracing

OpenTelemetry will produce traces.

Example:

```text
AcceptDelivery request

API Gateway             20 ms
Escrow Service          80 ms
PostgreSQL              15 ms
Outbox Insert            3 ms

async boundary

Kafka publish

Ledger Consumer
Ledger PostgreSQL       25 ms
```

This allows us to identify where latency occurs.

---

# 48. Logging

Logs will be structured.

Example:

```json
{
  "timestamp": "...",
  "level": "INFO",
  "service": "escrow-service",
  "correlationId": "...",
  "escrowId": "...",
  "event": "ESCROW_RELEASE_REQUESTED"
}
```

Logs must not contain:

* Passwords
* Tokens
* Private keys
* Full card details
* Sensitive identity information

---

# 49. Failure Isolation

A Search outage should cause:

```text
Search unavailable
```

not:

```text
Escrow funding unavailable
```

A Notification outage should cause:

```text
Emails delayed
```

not:

```text
Payment rollback
```

A Redis outage may cause:

```text
higher latency
```

not:

```text
incorrect balance
```

This is one of the major benefits of proper service boundaries.

---

# 50. Graceful Degradation

When optional systems fail:

```text
Elasticsearch down
→ direct lookup still works

Redis down
→ database fallback within limits

Notification service down
→ queue notification work

Analytics down
→ transactions continue
```

For critical financial dependencies:

```text
Ledger unavailable
→ financial operation must wait or fail safely
```

---

# 51. Example: Create Escrow Request

```text
Client
   ↓
API Gateway
   ↓
Escrow Service
   ↓
PostgreSQL
```

Inside one local transaction:

```text
INSERT escrow

INSERT terms

INSERT outbox event
```

Commit.

Response:

```text
201 Created
```

Later:

```text
Outbox Publisher
      ↓
Kafka
      ↓
Search
Audit
Risk
Notification
```

---

# 52. Example: Funding Flow

```text
Buyer
  ↓
API Gateway
  ↓
Payment Service
  ↓
Payment Provider
```

Provider eventually confirms payment.

```text
Provider
  ↓ webhook
Payment Service
  ↓
Payment DB
  ↓
PaymentSucceeded event
  ↓ Kafka
Ledger Service
```

Ledger posts funding journal.

```text
Ledger
  ↓
FundingSecured event
  ↓ Kafka
Escrow Service
```

Escrow moves:

```text
FUNDING_PROCESSING
→
FUNDED
```

---

# 53. Example: Release Flow

Buyer accepts delivery:

```text
Client
  ↓
Escrow Service
```

Escrow validates:

```text
state == INSPECTION
buyer authorised
no dispute
no hold
```

Then:

```text
INSPECTION
→ RELEASE_PENDING
```

and publishes:

```text
ReleaseRequested
```

Ledger consumes:

```text
ReleaseRequested
      ↓
Check idempotency
      ↓
Check available held funds
      ↓
Post balanced journal
      ↓
FundsReleased
```

Escrow consumes:

```text
FundsReleased
      ↓
RELEASE_PENDING
→
RELEASED
```

---

# 54. Example: Notification Flow

```text
EscrowReleased
      ↓ Kafka
Notification Service
      ↓
Creates notification records
      ↓
RabbitMQ
      |
      +--> Email Queue
      |
      +--> SMS Queue
      |
      +--> Push Queue
```

Worker failure does not affect financial truth.

---

# 55. Example: Search Projection

```text
EscrowReleased
      ↓ Kafka
Search Indexer
      ↓
Elasticsearch
```

For a brief period:

```text
PostgreSQL:
RELEASED

Elasticsearch:
RELEASE_PENDING
```

This temporary discrepancy is expected.

It is eventual consistency.

---

# 56. Example: Redis Cache Invalidation

Suppose:

```text
Redis:
escrow:123 = INSPECTION
```

Escrow changes to:

```text
RELEASE_PENDING
```

After database commit:

```text
cache invalidated
```

or:

```text
updated asynchronously
```

Financial decisions still read authoritative state where required.

---

# 57. High-Level Failure Scenario

Suppose:

```text
Payment succeeded

Ledger service temporarily unavailable
```

State:

```text
Payment DB:
SUCCESS

Escrow:
FUNDING_PROCESSING

Ledger:
not posted
```

Kafka retains:

```text
PaymentSucceeded
```

Ledger consumer eventually recovers.

Then:

```text
Ledger posting succeeds
```

and:

```text
FundingSecured
```

moves escrow to:

```text
FUNDED
```

This illustrates eventual consistency without losing financial correctness.

---

# 58. Architecture Layers

The complete system can be considered in layers:

```text
Edge Layer
   ↓
API Layer
   ↓
Domain Services
   ↓
Data Layer
   ↓
Messaging Layer
   ↓
Derived Read Systems
   ↓
Observability
```

But these layers should not create unnecessary runtime coupling.

---

# 59. Initial Technology Stack

## Application

```text
Java
Spring Boot
Spring Security
Spring Data JPA
Spring Kafka
Spring AMQP
Spring Cloud Gateway
Resilience4j
```

## Transactional data

```text
PostgreSQL
Flyway
HikariCP
```

## Event streaming

```text
Apache Kafka
```

## Work queues

```text
RabbitMQ
```

## Cache

```text
Redis
```

## Search

```text
Elasticsearch
```

## Object storage

```text
S3-compatible storage
```

## Containerisation

```text
Docker
```

## Orchestration

```text
Kubernetes
```

## Infrastructure

```text
Terraform
```

## Observability

```text
OpenTelemetry
Prometheus
Grafana
```

---

# 60. Development Environment

Local development will initially use Docker Compose.

Conceptually:

```text
docker compose
├── postgres
├── kafka
├── rabbitmq
├── redis
├── elasticsearch
└── observability components
```

We will not necessarily run every component from the first coding exercise.

Infrastructure will be introduced when the first use case requires it.

---

# 61. Production Architecture Principle

Development infrastructure and production architecture are not identical.

Example:

Development:

```text
One PostgreSQL container
```

Production:

```text
Multiple managed PostgreSQL clusters
```

Development:

```text
One Kafka broker
```

Production:

```text
Multi-broker Kafka cluster
```

The code should not depend on the development shortcut.

---

# 62. Architecture Decision Records

Major decisions must receive an ADR.

Future examples:

```text
ADR 0002:
Why Kafka and RabbitMQ both exist

ADR 0003:
Database ownership strategy

ADR 0004:
Transactional outbox approach

ADR 0005:
Ledger consistency model

ADR 0006:
REST versus gRPC

ADR 0007:
Redis caching strategy

ADR 0008:
Elasticsearch indexing strategy

ADR 0009:
Monorepo versus multi-repository

ADR 0010:
Kubernetes deployment strategy
```

---

# 63. Architecture Rules

The following rules are mandatory:

```text
No direct cross-service database writes.

No financial truth stored only in Redis.

No financial decision based solely on Elasticsearch.

No infinite retries.

No unbounded queues.

No remote call without timeout.

No financial command without idempotency.

No event consumer that assumes exactly-once delivery.

No state change plus Kafka publish without a reliability strategy.

No direct balance updates.

No shared mutable financial tables between services.

No service owns another service's business state.
```

---

# 64. Initial Implementation Path

The first implemented services will be:

```text
identity-service
escrow-service
payment-service
ledger-service
api-gateway
```

Initial infrastructure:

```text
PostgreSQL
Kafka
Redis
Docker Compose
```

RabbitMQ will be introduced when the first background work queue appears.

Elasticsearch will be introduced when we implement advanced transaction search.

This means every technology enters because of a concrete requirement.

---

# 65. First Vertical Slice

The first end-to-end implementation will be:

```text
Register user
    ↓
Create escrow
    ↓
Accept terms
    ↓
Initiate funding
    ↓
Confirm funding
    ↓
Post ledger funding journal
    ↓
Mark escrow FUNDED
```

This slice alone will teach:

* REST APIs
* Microservice boundaries
* PostgreSQL
* Database-per-service ownership
* HikariCP
* Authentication
* Idempotency
* Kafka
* Transactional outbox
* Consumer idempotency
* Distributed tracing
* Eventual consistency
* Failure recovery
* Load testing

---

# 66. Next Step

Before implementing services, the next document should define the first **event catalogue and command catalogue**.

It will specify:

```text
Commands
Events
Producers
Consumers
Partition keys
Ordering requirements
Delivery guarantees
Idempotency rules
Failure handling
```

That document will let us design Kafka deliberately rather than simply creating topics while coding.
