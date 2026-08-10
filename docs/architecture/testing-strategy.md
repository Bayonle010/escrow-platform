# Testing Strategy

**Status:** Draft
**Scope:** Functional correctness, distributed-system behaviour, concurrency, scale, and failure testing

## 1. Testing Principle

The system must be tested for more than:

```text
request → 200 OK
```

We must test:

```text
correctness
concurrency
duplicate delivery
partial failure
recovery
performance
```

Financial invariants are the highest-priority test target.

---

## 2. Unit Tests

Use JUnit and Mockito for isolated business logic.

Examples:

```text
Valid escrow transition succeeds

Invalid transition fails

Fee calculation is deterministic

Unbalanced journal is rejected

Refund above refundable balance is rejected
```

Unit tests should be fast and numerous.

---

## 3. Integration Tests

Use real infrastructure wherever behaviour matters.

Primary tool:

```text
Testcontainers
```

Containers may include:

```text
PostgreSQL
Kafka
Redis
RabbitMQ
```

Avoid replacing important infrastructure behaviour with mocks.

---

## 4. Database Tests

Test:

```text
Flyway migrations

unique constraints

optimistic locking

row locking

journal atomicity

idempotency constraints

outbox persistence
```

Important example:

```text
two concurrent releases
→ exactly one financial effect
```

---

## 5. Kafka Tests

Test:

```text
event produced correctly

consumer receives event

duplicate event is harmless

consumer crash does not duplicate business effect

outbox publishes after recovery

ordering preserved for same escrowId
```

---

## 6. RabbitMQ Tests

When introduced, test:

```text
job delivery

manual acknowledgement

redelivery

retry queues

dead-letter handling

worker idempotency
```

---

## 7. Contract Tests

Services must verify API and event contracts.

Test:

```text
REST request/response compatibility

event schema compatibility

producer serialization

consumer deserialization
```

Later we may use:

```text
Pact
Schema Registry compatibility checks
```

---

## 8. Concurrency Tests

Mandatory scenarios:

```text
two simultaneous funding confirmations

two simultaneous releases

release vs dispute

release vs refund

two payouts using one balance

inspection expiry vs dispute

duplicate provider webhook
```

Expected principle:

```text
one valid authoritative outcome
```

---

## 9. Failure Tests

Simulate:

```text
PostgreSQL unavailable

Kafka unavailable

Redis unavailable

consumer crashes

provider timeout

connection pool exhaustion

slow downstream service
```

Possible tooling:

```text
Toxiproxy
Testcontainers
```

---

## 10. Load Testing

Initial tool:

```text
k6
```

or Gatling where Java-based test scenarios are useful.

Testing stages:

```text
100 RPS

1,000 RPS

10,000 RPS

100,000 RPS

higher distributed tests later
```

At each stage measure:

```text
throughput

p95/p99 latency

error rate

DB connections

Kafka lag

CPU

memory

lock waits
```

---

## 11. Stress Testing

Stress tests intentionally exceed expected capacity.

Goal:

```text
Find where the system breaks.
```

We must determine:

```text
first bottleneck

failure behaviour

recovery behaviour

whether money remains correct
```

---

## 12. Spike Testing

Simulate sudden traffic changes.

Example:

```text
10,000 RPS
→ 100,000 RPS
within seconds
```

Observe:

```text
autoscaling

connection pools

rate limiting

Kafka lag

backpressure
```

---

## 13. Soak Testing

Run moderate traffic for long periods.

Purpose:

```text
memory leaks

connection leaks

consumer lag growth

DB bloat

resource exhaustion
```

---

## 14. Property-Based Financial Tests

Important properties include:

```text
For every journal:
debits = credits
```

```text
released + refunded
<= funded amount
```

```text
payout
<= available balance
```

```text
duplicate command
creates at most one financial effect
```

---

## 15. Test Pyramid

Approximate direction:

```text
Many unit tests

Fewer integration tests

Focused end-to-end tests

Targeted high-cost load/failure tests
```

Do not attempt to test every combination through slow end-to-end tests.

---

## 16. CI Requirements

Every pull request should eventually run:

```text
compile

unit tests

integration tests

Flyway validation

static analysis

dependency checks
```

Long-running performance tests may run separately.

---

## 17. Production Readiness Gate

A critical financial workflow is not production-ready until we have tested:

```text
happy path

duplicate request

concurrent request

service crash

message duplicate

message retry

database failure

provider timeout

recovery
```

---

## 18. Core Rule

The most valuable test is often:

```text
What happens when this fails halfway through?
```

not:

```text
Does it work when everything behaves perfectly?
```
