# First Vertical Slice

**Status:** Approved for Initial Implementation

## 1. Objective

The first implementation will prove one distributed financial workflow:

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
Ledger Secures Funds
      ↓
Escrow becomes FUNDED
```

This gives us enough scope to learn microservices deeply without implementing the entire product at once.

---

## 2. Initial Services

We will build:

```text
api-gateway

identity-service

escrow-service

payment-service

ledger-service
```

We will not yet build:

```text
dispute-service

payout-service

notification-service

search-service
```

Those enter later.

---

## 3. Initial Infrastructure

Required immediately:

```text
PostgreSQL

Kafka

Docker Compose
```

Also introduce:

```text
Redis
```

once we reach caching/rate limiting/idempotency acceleration.

RabbitMQ enters with notification/background workers.

Elasticsearch enters when we build advanced search.

---

## 4. Identity Flow

API:

```text
POST /api/v1/auth/register
```

Identity Service:

```text
validate user
hash password
persist user
insert outbox event
commit
```

Event:

```text
UserRegistered
```

---

## 5. Create Escrow Flow

API:

```text
POST /api/v1/escrows
```

Escrow Service validates:

```text
buyer != seller

amount > 0

currency supported

participants valid
```

Database transaction:

```text
insert escrow

insert terms version 1

insert outbox event

commit
```

Event:

```text
EscrowCreated
```

---

## 6. Accept Terms Flow

API:

```text
POST /api/v1/escrows/{escrowId}/accept-terms
```

Validate:

```text
caller is counterparty

terms version matches

escrow state valid
```

Transition:

```text
AWAITING_COUNTERPARTY
→
AWAITING_FUNDING
```

Use optimistic locking.

---

## 7. Initiate Funding

API:

```text
POST /api/v1/escrows/{escrowId}/fund
```

Required:

```text
Idempotency-Key
```

Payment Service creates:

```text
Payment
status = PROCESSING
```

For initial development, the external provider may be simulated.

Later we integrate a real provider adapter.

---

## 8. Payment Confirmation

Simulated/provider callback confirms:

```text
PaymentSucceeded
```

Payment Service transaction:

```text
update payment

insert outbox event

commit
```

Kafka topic:

```text
payment.events.v1
```

Partition key:

```text
escrowId
```

---

## 9. Ledger Funding

Ledger Service consumes:

```text
PaymentSucceeded
```

It must:

```text
deduplicate event

create unique funding journal

post balanced entries

update balance projection

insert outbox event
```

Example:

```text
Debit:
Provider Clearing

Credit:
Escrow Held
```

Event:

```text
EscrowFundingSecured
```

Kafka topic:

```text
ledger.events.v1
```

---

## 10. Escrow Funding Completion

Escrow Service consumes:

```text
EscrowFundingSecured
```

It:

```text
deduplicates event

validates aggregate version/state

transitions:
FUNDING_PROCESSING
→ FUNDED

inserts outbox event
```

Publishes:

```text
EscrowFunded
```

The first distributed workflow is now complete.

---

## 11. Required Patterns

The first vertical slice must already include:

```text
UUIDv7

Flyway

PostgreSQL

HikariCP

DTO validation

central error handling

optimistic locking

idempotency

transactional outbox

Kafka producer

Kafka consumer

consumer inbox/deduplication

correlation IDs

structured logging

OpenTelemetry-ready instrumentation

Testcontainers
```

---

## 12. Initial Data Ownership

```text
identity-service
→ identity_db

escrow-service
→ escrow_db

payment-service
→ payment_db

ledger-service
→ ledger_db
```

Development may use one PostgreSQL instance with separate databases.

No cross-service table access.

---

## 13. Initial Kafka Topics

```text
identity.events.v1

escrow.events.v1

payment.events.v1

ledger.events.v1
```

Default business partition key:

```text
escrowId
```

where escrow ordering matters.

---

## 14. Initial Failure Scenarios

Before considering the slice complete, test:

```text
duplicate funding API request

duplicate provider callback

duplicate PaymentSucceeded event

Ledger unavailable

Kafka unavailable

Ledger commits but consumer crashes

Escrow receives duplicate EscrowFundingSecured

two funding confirmations concurrently
```

Expected result:

```text
Escrow funded exactly once.

Ledger funding journal created exactly once.
```

---

## 15. Initial Load Goal

First milestone:

```text
1,000 RPS
```

with correctness preserved.

Then:

```text
10,000 RPS
```

after identifying and resolving initial bottlenecks.

We scale progressively from measured results.

---

## 16. Definition of Done

The vertical slice is complete when:

```text
User can register.

Escrow can be created.

Counterparty can accept terms.

Funding can be initiated.

Payment confirmation is idempotent.

PaymentSucceeded reaches Kafka.

Ledger posts one balanced funding journal.

EscrowFundingSecured reaches Kafka.

Escrow becomes FUNDED exactly once.

Duplicate requests/events do not duplicate money.

Failures are observable.

Integration tests pass.
```

---

## 17. First Build Order

Implementation order:

```text
1. Repository/service structure

2. Local Docker infrastructure

3. Identity Service

4. Escrow Service

5. Payment Service

6. Ledger Service

7. Kafka

8. Transactional outbox

9. Consumer inbox

10. Full funding workflow

11. Failure tests

12. Load tests
```

After this we add:

```text
Redis
RabbitMQ
Elasticsearch
Resilience patterns
Kubernetes
```

through real platform requirements.
