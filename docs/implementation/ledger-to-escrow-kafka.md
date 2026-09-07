# Ledger-to-Escrow Kafka Implementation

## Goal

Consume `EscrowFundingSecured` from `ledger.events.v1` and transition the
authoritative Escrow aggregate from `AWAITING_FUNDING` to `FUNDED` exactly once
at the business level.

```text
ledger.events.v1
  -> validate EscrowFundingSecured envelope and payload
  -> claim eventId in the Escrow consumer inbox
  -> lock the escrow row
  -> verify state, amount, and currency
  -> transition escrow to FUNDED
  -> insert EscrowFunded into the Escrow outbox
  -> commit the database transaction
  -> commit the Kafka offset
```

## Consumer Boundary

The listener processes only `EscrowFundingSecured`. It verifies required
envelope metadata, aggregate type, event version, journal ID, correlation ID,
causation ID, timestamp, and Jakarta payload constraints before invoking the
domain transaction. Other valid Ledger event types on the shared topic are
ignored.

Historical Ledger outbox rows may contain payload timestamps with nanoseconds
while their envelope timestamps have PostgreSQL microsecond precision. The
consumer accepts a difference below one microsecond. Newly created events are
normalized before storage and match exactly.

## Exactly-Once Business Effect

Kafka delivery remains at least once. The Escrow database migration adds a
`consumer_inbox` table keyed by consumer name and `eventId`. The inbox claim,
escrow update, and `EscrowFunded` outbox insert share one transaction.

If processing fails, all three operations roll back and Kafka can redeliver. If
the database commits but the process stops before committing its Kafka offset,
the duplicate inbox claim fails and the listener returns the existing result
without another transition or outbox event.

The escrow row is selected with a pessimistic write lock. The transition is
accepted only from `AWAITING_FUNDING`, and the event amount and currency must
match the authoritative escrow terms.

## Traceability

The migration also adds nullable `causation_id` to existing Escrow outbox rows.
New `EscrowFunded` events record the incoming Ledger `eventId` as their
causation ID and retain the original correlation ID. The payload contract is
[`contracts/events/escrow-funded-v1.schema.json`](../../contracts/events/escrow-funded-v1.schema.json).

## Configuration

| Variable | Default | Purpose |
| --- | --- | --- |
| `KAFKA_BOOTSTRAP_SERVERS` | `localhost:9092` | Kafka connection |
| `LEDGER_EVENTS_TOPIC` | `ledger.events.v1` | Source topic |
| `ESCROW_LEDGER_CONSUMER_GROUP` | `escrow-funding-secured-v1` | Stable consumer group |
| `ESCROW_LEDGER_CONSUMER_CONCURRENCY` | `3` | Listener threads |
| `ESCROW_MESSAGING_CONSUMER_ENABLED` | `true` | Enables the listener |

Compose makes Escrow Service wait for `kafka-init` and supplies the internal
broker address `kafka:19092`.

## Verification

Unit tests cover mapping, validation, historical timestamp tolerance, first
delivery, duplicate replay, event-ID conflict, financial mismatch, invalid
state, and `EscrowFunded` creation. The Testcontainers integration test sends
the same Kafka record twice and verifies one inbox row, one version increment,
state `FUNDED`, and one causally linked Escrow outbox event.
