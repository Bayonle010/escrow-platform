# Service Communication Strategy

**Status:** Draft
**Scope:** Communication between services and infrastructure

## 1. Communication Models

The platform uses three primary communication styles:

```text
REST/gRPC
→ Immediate answer required

Kafka
→ Durable business fact occurred

RabbitMQ
→ Background work must be performed
```

No single communication mechanism should be used for every problem.

---

## 2. REST

Use synchronous REST when the caller requires an immediate authoritative response.

Examples:

```text
API Gateway → Escrow Service

Escrow Service → Identity Service
```

Typical questions:

```text
Does this resource exist?

Is this user authorised?

What is the current authoritative state?
```

REST calls must have:

* Connection timeout
* Request timeout
* Correlation ID
* Authentication
* Bounded retries where safe
* Metrics and tracing

---

## 3. gRPC

gRPC may later be introduced for internal high-throughput communication where:

* Strong contracts are valuable
* Low serialization overhead matters
* Streaming is useful
* Both services are controlled internally

We will not introduce gRPC until measurements show a real need.

REST remains the initial synchronous protocol.

---

## 4. Kafka

Kafka carries durable business events.

Examples:

```text
PaymentSucceeded
EscrowFundingSecured
EscrowFunded
EscrowReleased
DisputeOpened
```

Use Kafka when:

* Multiple consumers care about the event
* Consumers should be decoupled
* Replay is useful
* High throughput is required
* Temporary consumer failure should not block the producer

Consumers must assume:

```text
at-least-once delivery
```

Therefore they must be idempotent.

---

## 5. RabbitMQ

RabbitMQ carries worker-oriented commands and jobs.

Examples:

```text
SendEmail
SendSMS
GenerateReceipt
ScanEvidence
GenerateStatement
RetryProviderTask
```

Use RabbitMQ when:

* Work needs to be assigned to workers
* Competing consumers are appropriate
* Queue depth represents pending work
* Retry and dead-letter routing are important

---

## 6. Kafka vs RabbitMQ

Simple guideline:

```text
Kafka:
Something happened.

RabbitMQ:
Please do this work.
```

Example:

```text
EscrowReleased
→ Kafka
```

Then:

```text
GenerateReleaseReceipt
→ RabbitMQ
```

The distinction is not absolute, but every exception must have a clear reason.

---

## 7. No Direct Cross-Service Database Access

This is prohibited:

```text
Escrow Service
→ Ledger Database
```

Use:

```text
Escrow Service
→ Ledger Service
```

through an approved command/API/event workflow.

Each service owns its data.

---

## 8. Timeouts

Every synchronous remote call must have bounded timeouts.

Never:

```text
wait indefinitely
```

Initial timeout policy will distinguish:

```text
connection timeout
request timeout
overall business deadline
```

Exact values will be tuned through testing.

---

## 9. Retries

Retries are allowed only for transient failures and safe operations.

Use:

```text
bounded attempts
+
exponential backoff
+
jitter
+
idempotency
```

Do not retry permanent business failures such as:

```text
invalid state
unsupported currency
authorization failure
insufficient balance
```

---

## 10. Retry Storm Protection

A failing dependency combined with aggressive retries can multiply traffic.

Example:

```text
10,000 requests/sec
× 3 immediate retries
=
40,000 calls/sec
```

Retries must therefore be controlled.

---

## 11. Circuit Breakers

Use circuit breakers for unstable external or internal dependencies.

Example:

```text
Payment Service
    ↓
Circuit Breaker
    ↓
Payment Provider
```

Potential library:

```text
Resilience4j
```

A circuit breaker prevents continuously sending traffic to a failing dependency.

---

## 12. Bulkheads

Resources for one dependency should not consume resources required by another.

Examples:

```text
Provider A connection pool

Provider B connection pool

Notification executor
```

A slow provider must not exhaust every application thread or connection.

---

## 13. Backpressure

Every communication layer requires backpressure.

Examples:

```text
HTTP
→ rate limiting / request rejection

Database
→ bounded connection pool

Kafka
→ consumer lag monitoring

RabbitMQ
→ queue depth and bounded consumers

Thread pools
→ bounded queues
```

Unlimited queues are prohibited.

---

## 14. Eventual Consistency

Asynchronous communication means services may temporarily disagree.

Example:

```text
Payment Service:
SUCCESS

Ledger:
not posted yet

Escrow:
FUNDING_PROCESSING
```

This is acceptable temporarily.

Eventually:

```text
Ledger posts funding
→ EscrowFundingSecured
→ Escrow becomes FUNDED
```

The system must make intermediate states explicit.

---

## 15. Transactional Outbox

Whenever a service changes its database and must publish an event:

```text
BEGIN

business change

insert outbox event

COMMIT
```

The outbox publisher later sends the event to Kafka.

This prevents committed state from losing its corresponding event.

---

## 16. Consumer Idempotency

Consumers must safely handle duplicate events.

Protection includes:

```text
eventId
consumer inbox
unique constraints
business idempotency
```

A duplicate message must not create a duplicate financial effect.

---

## 17. Correlation

Correlation IDs must propagate through:

```text
HTTP

Kafka

RabbitMQ

scheduled jobs

provider callbacks
```

This allows one business operation to be traced across the distributed system.

---

## 18. Failure Rules

```text
Notification unavailable
→ financial transaction continues

Search unavailable
→ core transaction continues

Redis unavailable
→ slower/degraded requests

Kafka unavailable
→ outbox retains events

Ledger unavailable
→ financial write fails or waits safely

Payment provider timeout
→ outcome may become UNKNOWN
```

Unknown is a valid distributed-system state.

---

## 19. Initial Communication Flow

First vertical slice:

```text
Client
  ↓ REST
Payment Service
  ↓
Payment Provider

PaymentSucceeded
  ↓ Kafka

Ledger Service
  ↓
Post funding journal

EscrowFundingSecured
  ↓ Kafka

Escrow Service
  ↓
FUNDED
```

Notifications will later use:

```text
EscrowFunded
  ↓ Kafka
Notification Service
  ↓ RabbitMQ
Email Worker
```

---

## 20. Core Rules

```text
Use REST when an immediate answer is required.

Use Kafka for durable business facts.

Use RabbitMQ for worker-oriented jobs.

No remote call without timeout.

No unlimited retries.

No unlimited queues.

No cross-service database access.

No event consumer that assumes one-time delivery.

Do not hold database transactions open during remote calls.

Do not make optional downstream services part of critical financial transactions.
```
