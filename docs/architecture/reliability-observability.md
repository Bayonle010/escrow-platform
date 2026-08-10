# Reliability and Observability Architecture

**Status:** Draft
**Scope:** Failure handling, resilience, monitoring, tracing, logging, alerting

## 1. Reliability Principle

The system must assume:

```text
services fail
networks timeout
messages duplicate
databases become slow
brokers become unavailable
external providers return uncertain results
```

Partial failure is normal.

Critical financial operations must fail safely.

---

## 2. Timeouts

Every remote call must have bounded timeouts.

Applies to:

```text
REST calls
payment providers
payout providers
Redis
Elasticsearch
database connections
```

Never wait indefinitely.

---

## 3. Retries

Retry only transient failures.

Use:

```text
bounded retries
+
exponential backoff
+
jitter
+
idempotency
```

Do not retry permanent failures such as:

```text
invalid state
insufficient funds
unauthorized request
invalid currency
```

---

## 4. Circuit Breakers

Use circuit breakers around unstable dependencies.

Example:

```text
Payment Service
      ↓
Circuit Breaker
      ↓
Provider
```

Initial implementation:

```text
Resilience4j
```

Circuit breakers must expose metrics.

---

## 5. Bulkheads

One failing dependency must not consume all resources.

Separate or bound:

```text
thread pools
HTTP connection pools
provider connections
worker capacity
```

Provider A failure must not take down Provider B.

---

## 6. Backpressure

Every system boundary must have capacity limits.

Examples:

```text
API
→ rate limiting

Spring service
→ bounded executors

PostgreSQL
→ bounded HikariCP pool

Kafka
→ consumer lag monitoring

RabbitMQ
→ queue depth monitoring
```

Unlimited queues are prohibited.

---

## 7. Graceful Degradation

Examples:

```text
Redis unavailable
→ slower reads

Elasticsearch unavailable
→ advanced search unavailable

Notification service unavailable
→ notifications delayed

Analytics unavailable
→ transaction processing continues
```

But:

```text
Ledger unavailable
→ financial operation fails safely
```

---

## 8. Unknown Outcomes

External financial operations may have:

```text
SUCCESS
FAILED
UNKNOWN
```

Example:

```text
payout provider receives request
provider processes payout
network times out
```

The platform must not immediately classify this as failure.

Use reconciliation to determine final truth.

---

## 9. Kafka Failure

Business state and event intent are committed together using the transactional outbox.

If Kafka is unavailable:

```text
business transaction commits
outbox event remains pending
publisher retries later
```

Kafka failure must not silently lose events.

---

## 10. Consumer Failure

Kafka consumers must support:

```text
idempotency
bounded retries
dead-letter handling
lag monitoring
```

A crash after database commit must not duplicate the business effect after redelivery.

---

## 11. RabbitMQ Failure

RabbitMQ workers must use:

```text
manual acknowledgements
bounded retries
dead-letter queues
prefetch limits
idempotent processing where required
```

A worker crash must not silently lose required work.

---

## 12. Database Failure

Possible outcomes include:

```text
transaction rollback
connection failure
deadlock
commit ambiguity
failover
```

Financial requests must use idempotency so clients can safely retry after uncertain outcomes.

---

## 13. Redis Failure

Redis is not authoritative.

Redis outage may cause:

```text
cache misses
higher latency
reduced rate-limit efficiency
temporary feature degradation
```

It must not cause incorrect balances.

---

## 14. Elasticsearch Failure

Search indexing may temporarily stop.

Kafka retains events.

When indexing resumes:

```text
consumer continues from previous offset
```

Search projection lag must be measurable.

---

## 15. Graceful Shutdown

Before a service stops:

```text
stop accepting new work
stop fetching new messages
finish or safely abandon active work
release resources
terminate
```

This is required for Kubernetes rolling deployments.

---

# Observability

## 16. Correlation IDs

Every request receives a correlation ID.

It must propagate through:

```text
API Gateway
REST calls
Kafka events
RabbitMQ jobs
provider callbacks
scheduled jobs
```

Example:

```text
X-Correlation-Id: 019c...
```

---

## 17. Distributed Tracing

Use:

```text
OpenTelemetry
```

Traces should show workflows such as:

```text
Client
→ API Gateway
→ Escrow Service
→ PostgreSQL
→ Kafka
→ Ledger Service
→ PostgreSQL
```

Async spans must preserve trace context where possible.

---

## 18. Structured Logging

Logs must be machine-readable.

Example:

```json
{
  "level": "INFO",
  "service": "ledger-service",
  "correlationId": "019c...",
  "escrowId": "019c...",
  "event": "ESCROW_FUNDING_SECURED"
}
```

Do not log sensitive credentials.

---

## 19. Metrics

Use:

```text
Micrometer
Prometheus
Grafana
```

Important API metrics:

```text
requests/sec
error rate
p50
p95
p99
```

---

## 20. Database Metrics

Monitor:

```text
active connections
idle connections
waiting threads
connection acquisition time
query latency
deadlocks
lock waits
replication lag
```

HikariCP saturation must generate alerts.

---

## 21. Kafka Metrics

Monitor:

```text
producer throughput
consumer throughput
consumer lag
failed publishes
retry count
DLT count
partition skew
```

Consumer lag is one of the most important event-system health signals.

---

## 22. RabbitMQ Metrics

Monitor:

```text
queue depth
publish rate
delivery rate
unacked messages
redeliveries
consumer count
dead-letter count
```

---

## 23. Redis Metrics

Monitor:

```text
cache hit ratio
memory usage
evictions
latency
expired keys
hot keys
replication health
```

---

## 24. Elasticsearch Metrics

Monitor:

```text
search latency
indexing latency
rejected requests
heap usage
disk usage
shard health
projection lag
```

---

## 25. Business Metrics

Technical health alone is insufficient.

Monitor:

```text
escrows created
escrows funded
funds held
funds released
refund count
payout failures
dispute rate
reconciliation differences
```

Critical metric:

```text
ledger imbalance count = 0
```

Any non-zero value is a critical incident.

---

## 26. Initial SLOs

Critical APIs:

```text
availability = 99.99%
```

Initial latency targets:

```text
cached read p95 < 100 ms

standard read p95 < 250 ms

internal state change p95 < 500 ms

financial acknowledgement p95 < 1 second
```

These targets will be validated through load testing.

---

## 27. Alerts

Critical alerts include:

```text
ledger imbalance

negative available balance

database pool saturation

Kafka consumer lag

outbox backlog

RabbitMQ DLT growth

payment UNKNOWN too long

payout UNKNOWN too long

reconciliation mismatch

high API error rate

p99 latency spike
```

---

## 28. Dashboards

Initial Grafana dashboards:

```text
Platform Overview

Escrow Service

Payment Service

Ledger Service

Kafka

PostgreSQL

Redis

RabbitMQ

Elasticsearch

Financial Operations
```

---

## 29. Incident Investigation

For any production incident, an engineer should be able to answer:

```text
What failed?

When did it start?

Which services are affected?

Which users are affected?

Is money at risk?

Which correlation IDs are involved?

Can the system recover automatically?

Is reconciliation required?
```

---

## 30. Core Rules

```text
No remote call without timeout.

No unlimited retries.

No unlimited queues.

Every critical workflow must be observable.

Every request needs correlation.

Financial uncertainty must remain visible.

Optional dependency failure should degrade, not corrupt.

Metrics must include business health, not only CPU and memory.

A financial system that cannot be investigated is not production-ready.
```
