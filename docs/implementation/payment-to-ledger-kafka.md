# Payment-to-Ledger Kafka Implementation

## Goal

Deliver payment events from the Payment Service database to Kafka without losing
committed events, then apply `PaymentSucceeded` in Ledger exactly once at the
business level even when Kafka delivers a record more than once.

The resulting flow is:

```text
Provider callback
  -> payment and outbox row commit together
  -> polling publisher sends the outbox row to payment.events.v1
  -> Ledger Kafka consumer receives PaymentSucceeded
  -> inbox row, balanced journal, balances, and ledger outbox row commit together
```

## 1. Payment Transaction Boundary

`PaymentConfirmationService` already changes the payment to `SUCCEEDED` and
inserts `PaymentSucceeded` into `outbox_events` in the same PostgreSQL
transaction. Kafka is deliberately not called from that request transaction.

This prevents the two event-loss cases caused by a direct database-then-Kafka
implementation:

* The payment cannot commit without a durable event waiting to be published.
* A temporary Kafka outage does not fail or roll back a valid provider callback.

## 2. Selecting Work Safely

`OutboxEventRepository.lockNextBatch` selects only due `PENDING` rows using:

```sql
ORDER BY occurred_at, event_id
LIMIT :batchSize
FOR UPDATE SKIP LOCKED
```

`FOR UPDATE` prevents another publisher from taking the same row while the first
publisher owns the database transaction. `SKIP LOCKED` lets other application
replicas continue with different rows instead of waiting on the lock.

The initial implementation holds the row lock while waiting for Kafka's
acknowledgement. This is simple and correct for the first vertical slice. Batch
size and publish timeout bound how long the transaction can hold locks.

## 3. Event Envelope and Partitioning

The stored business payload is wrapped in a transport envelope containing:

* `eventId`
* aggregate type and ID
* event type and version
* occurrence time
* correlation ID
* the immutable business payload

The JSON Schema is stored at
`contracts/events/payment-succeeded-v1.schema.json`.

Every payment record uses `escrowId` as its Kafka key. Kafka therefore assigns
events for the same escrow to the same partition and preserves their order.

## 4. Publication Result

The publisher waits for the `KafkaTemplate.send` future with a configured
timeout. Spring Kafka documents that `send` returns a `CompletableFuture` and
recommends a bounded `get` when the caller needs to wait for the result.

After acknowledgement:

```text
status       = PUBLISHED
attempts     = attempts + 1
published_at = current UTC time
```

For a broker error or timeout:

```text
status          = PENDING
attempts        = attempts + 1
next_attempt_at = now + exponential backoff
```

Backoff starts at one second and is capped at five minutes by default. Invalid
stored JSON or a missing/invalid `escrowId` is deterministic, so the row becomes
`FAILED` instead of being retried forever.

## 5. Why Delivery Is At Least Once

PostgreSQL and Kafka do not participate in one distributed transaction here.
This sequence is possible:

```text
Kafka accepts the record
  -> process crashes before PostgreSQL commits PUBLISHED
  -> publisher sends the record again after restart
```

The event is not lost, but it can be duplicated. This is intentional
at-least-once delivery. Correctness is completed at the consumer using the
event's stable `eventId`.

## 6. Ledger Consumer Boundary

`PaymentSucceededKafkaConsumer` reads String JSON records from
`payment.events.v1`. It ignores other valid payment event types because the
topic is shared by all Payment Service events.

For `PaymentSucceeded`, it rejects the record before financial processing when:

* required envelope metadata is missing;
* the aggregate type is not `Payment`;
* envelope and payload type, version, timestamp, correlation ID, or payment ID
  disagree;
* the payload status is not `SUCCEEDED`; or
* existing Jakarta validation constraints fail.

Valid data is mapped to the existing `PaymentSucceededEvent` domain record and
passed to `LedgerFundingService.secure`.

## 7. Ledger Transaction and Offset Ordering

The consumer uses record acknowledgement with Kafka auto-commit disabled.
`LedgerFundingService.secure` completes its PostgreSQL transaction before the
listener returns. Only then may the listener container commit the Kafka offset.

The database transaction contains:

* the consumer inbox claim keyed by consumer name and `eventId`;
* one balanced funding journal;
* two ledger entries;
* two balance updates; and
* one `EscrowFundingSecured` outbox event.

If the process crashes after the database commit but before the Kafka offset
commit, Kafka redelivers the event. The inbox claim detects it and returns the
existing journal, so money is not posted twice.

## 8. Configuration

Important environment variables are:

| Variable | Default | Purpose |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka connection used by either service |
| `PAYMENT_EVENTS_TOPIC` | `payment.events.v1` | Payment event topic |
| `PAYMENT_OUTBOX_POLL_INTERVAL` | `500ms` | Delay between publisher polls |
| `PAYMENT_OUTBOX_BATCH_SIZE` | `100` | Maximum locked rows per poll |
| `PAYMENT_OUTBOX_PUBLISH_TIMEOUT` | `5s` | Maximum wait for broker acknowledgement |
| `PAYMENT_OUTBOX_INITIAL_RETRY_DELAY` | `1s` | First transient-failure delay |
| `PAYMENT_OUTBOX_MAX_RETRY_DELAY` | `5m` | Retry-delay cap |
| `LEDGER_PAYMENT_CONSUMER_GROUP` | `ledger-payment-succeeded-v1` | Stable Ledger consumer group |
| `LEDGER_PAYMENT_CONSUMER_CONCURRENCY` | `3` | Listener threads, matching local topic partitions |

Compose uses `kafka:19092` inside the container network. Host-side tools use
`localhost:9092`.

## 9. Verification

Fast unit tests cover serialization, partition-key selection, acknowledgement,
retry backoff, permanent serialization failure, event filtering, event mapping,
and boundary validation.

Docker-backed tests add these checks:

* `PaymentOutboxPublisherIntegrationTest` uses PostgreSQL and Kafka to prove one
  due row becomes one Kafka record and a `PUBLISHED` row.
* `PaymentSucceededKafkaIntegrationTest` publishes the same Kafka event twice
  and proves Ledger creates one inbox row, one journal, two balanced entries,
  two balances, and one ledger outbox event.

Run everything from the repository root:

```bash
./mvnw test
```

Docker must be available for the Testcontainers tests. They skip automatically
when Docker is unavailable.

## 10. Following Increment

Ledger outbox publication is implemented separately. The remaining messaging
increment is:

```text
ledger.events.v1
  -> Escrow Service consumes EscrowFundingSecured with an inbox
  -> transition the escrow to FUNDED exactly once
```

See [Ledger outbox Kafka implementation](ledger-outbox-kafka.md).
