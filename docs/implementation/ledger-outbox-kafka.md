# Ledger Outbox Kafka Implementation

## Goal

Publish the `EscrowFundingSecured` event created in the Ledger database to
`ledger.events.v1` without losing a committed Ledger result.

```text
PaymentSucceeded consumed
  -> journal, entries, balances, inbox, and Ledger outbox row commit together
  -> polling publisher locks the due outbox row
  -> event is published to ledger.events.v1 using escrowId as the key
  -> Kafka acknowledgement is received
  -> outbox row becomes PUBLISHED
```

## Transaction and Delivery Model

The publisher selects due `PENDING` rows in occurrence order with
`FOR UPDATE SKIP LOCKED`. This prevents two Ledger replicas from publishing the
same row concurrently while allowing them to work on different rows.

Kafka and PostgreSQL do not share a transaction. A crash after Kafka accepts a
record but before the database marks it `PUBLISHED` can therefore cause a
duplicate. Delivery is intentionally at least once; the Escrow consumer must
deduplicate by the stable `eventId`.

## Event Contract

The event contract is
[`contracts/events/escrow-funding-secured-v1.schema.json`](../../contracts/events/escrow-funding-secured-v1.schema.json).
The envelope includes correlation and causation IDs, and both the Kafka key and
the payload `escrowId` must match the stored outbox partition key.

Ledger event timestamps are truncated to PostgreSQL microsecond precision
before both payload and outbox metadata are created. This keeps envelope and
payload timestamps identical after the row is read back for publication.

## Publication Outcomes

After Kafka acknowledgement, the row becomes `PUBLISHED`, its attempt count is
incremented, and `published_at` is recorded. Broker errors and timeouts keep the
row `PENDING` and apply exponential retry backoff from one second up to five
minutes. Malformed stored events become `FAILED` because retry cannot repair
their data.

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `LEDGER_EVENTS_TOPIC` | `ledger.events.v1` | Destination topic |
| `LEDGER_OUTBOX_PUBLISHER_ENABLED` | `true` | Enables scheduled publication |
| `LEDGER_OUTBOX_POLL_INTERVAL` | `500ms` | Delay between polls |
| `LEDGER_OUTBOX_BATCH_SIZE` | `100` | Maximum locked rows per poll |
| `LEDGER_OUTBOX_PUBLISH_TIMEOUT` | `5s` | Kafka acknowledgement timeout |
| `LEDGER_OUTBOX_INITIAL_RETRY_DELAY` | `1s` | First retry delay |
| `LEDGER_OUTBOX_MAX_RETRY_DELAY` | `5m` | Retry delay cap |

Compose creates `ledger.events.v1` with three partitions. The partition key is
`escrowId`, preserving order for one escrow while allowing different escrows to
be processed concurrently.

## Verification

Unit tests cover envelope serialization, partition-key validation, successful
acknowledgement, transient retry, permanent malformed-event failure, and
timestamp precision. A Testcontainers integration test proves a due PostgreSQL
row becomes one Kafka record and a `PUBLISHED` outbox row.

The next increment is consuming `EscrowFundingSecured` in the Escrow Service
with inbox deduplication and transitioning the escrow to `FUNDED` exactly once.
