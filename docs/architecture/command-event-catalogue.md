# Escrow Platform Command and Event Catalogue

**Version:** 1.0
**Status:** Draft
**System:** General-Purpose Escrow Platform

---

# 1. Purpose

This document defines how business actions and business facts move through the escrow platform.

It establishes:

* Commands
* Domain events
* Integration events
* Kafka topics
* Producers
* Consumers
* Partition keys
* Ordering requirements
* Idempotency rules
* Retry behaviour
* Dead-letter handling
* Schema evolution
* Correlation and causation

The purpose is to prevent the architecture from degenerating into:

```text
Create random Kafka topic
→ send random JSON
→ let every service consume everything
```

Messaging must reflect domain ownership.

---

# 2. Core Definitions

## Command

A command represents a request for something to happen.

Examples:

```text
CreateEscrow
AcceptTerms
ConfirmFunding
ReleaseEscrowFunds
OpenDispute
```

A command may succeed or fail.

A command is written in the imperative.

Examples:

```text
ReleaseFunds
CreatePayout
CancelEscrow
```

---

## Event

An event represents a fact that has already happened.

Examples:

```text
EscrowCreated
TermsAccepted
PaymentSucceeded
FundingSecured
FundsReleased
```

Events are written in the past tense.

An event must not claim that something happened before the authoritative service actually committed it.

---

# 3. Command Versus Event

Suppose the buyer accepts delivery.

The request is:

```text
AcceptDelivery
```

That is a command.

After the Escrow Service validates and commits the change:

```text
DeliveryAccepted
```

is an event.

Then Escrow may request a financial operation:

```text
ReleaseEscrowFunds
```

That is another command.

After the Ledger Service successfully posts the journal:

```text
EscrowFundsReleased
```

becomes an event.

Therefore:

```text
COMMAND
    ↓
business validation
    ↓
state change
    ↓
EVENT
```

---

# 4. Commands Can Fail

For example:

```text
ReleaseEscrowFunds
```

may fail because:

```text
Escrow is disputed
Escrow is frozen
Funds already released
Insufficient held balance
Invalid currency
Duplicate request
```

But:

```text
EscrowFundsReleased
```

means the release already happened.

Consumers should not interpret it as a request to attempt release.

---

# 5. Domain Events Versus Integration Events

A domain event exists within the domain model.

Example:

```text
PaymentSucceeded
```

The Payment domain may then transform it into a stable integration event published for other services.

Example:

```text
payment.succeeded.v1
```

This separation allows internal domain models to evolve without exposing every implementation detail externally.

For the first implementation, we may use one event object for both roles, but the distinction must remain understood.

---

# 6. Event Envelope

Every integration event will use a standard envelope.

Conceptual structure:

```json
{
  "eventId": "0195d...",
  "eventType": "payment.succeeded",
  "eventVersion": 1,
  "aggregateType": "payment",
  "aggregateId": "pay_123",
  "aggregateVersion": 8,
  "occurredAt": "2026-08-05T10:24:32Z",
  "producer": "payment-service",
  "correlationId": "cor_123",
  "causationId": "cmd_456",
  "partitionKey": "escrow_789",
  "payload": {}
}
```

---

# 7. Event ID

Every event must have a globally unique identifier:

```text
eventId
```

Recommended identifier:

```text
UUIDv7
```

Benefits include:

* Global uniqueness
* Rough time ordering
* Efficient database indexing compared with purely random UUIDs
* Consumer deduplication
* Auditability

---

# 8. Correlation ID

The correlation ID represents the larger business workflow.

Example:

```text
Buyer funds escrow
```

may involve:

```text
HTTP request
Payment Service
Kafka
Ledger Service
Kafka
Escrow Service
Kafka
Notification Service
```

All of these should preserve:

```text
correlationId = cor_ABC
```

This allows distributed tracing and production investigation.

---

# 9. Causation ID

The causation ID identifies what directly caused an event.

Example:

```text
Command:
AcceptDelivery
commandId = cmd_A
```

produces:

```text
DeliveryAccepted
eventId = evt_B
causationId = cmd_A
```

That event causes:

```text
ReleaseEscrowFunds
commandId = cmd_C
causationId = evt_B
```

This creates a chain:

```text
cmd_A
  ↓
evt_B
  ↓
cmd_C
  ↓
evt_D
```

---

# 10. Aggregate Version

Events produced by stateful aggregates should contain an aggregate version.

Example:

```text
escrowId = escrow_123
```

Events:

```text
EscrowCreated
aggregateVersion = 1

TermsAccepted
aggregateVersion = 2

FundingInitiated
aggregateVersion = 3

EscrowFunded
aggregateVersion = 4
```

Consumers can detect:

```text
duplicate events
stale events
missing events
out-of-order events
```

---

# 11. Kafka Topic Strategy

We will avoid one Kafka topic per individual event type.

For example, this becomes difficult to operate:

```text
escrow-created
escrow-submitted
escrow-funded
escrow-delivered
escrow-released
escrow-cancelled
...
```

Instead, initial topics should generally follow bounded contexts.

Proposed topics:

```text
escrow.events.v1
payment.events.v1
ledger.events.v1
payout.events.v1
dispute.events.v1
identity.events.v1
risk.events.v1
```

This may evolve as throughput requirements become clearer.

---

# 12. Why Domain-Oriented Topics

For example:

```text
escrow.events.v1
```

may contain:

```text
EscrowCreated
TermsAccepted
EscrowFunded
DeliverySubmitted
DeliveryAccepted
EscrowReleased
EscrowCancelled
```

Benefits:

* Related events share ownership.
* Topic proliferation is controlled.
* Partitioning strategy can align with aggregate identity.
* Consumer configuration becomes manageable.
* Domain responsibility remains clear.

---

# 13. Escrow Events Topic

Topic:

```text
escrow.events.v1
```

Producer:

```text
escrow-service
```

Primary partition key:

```text
escrowId
```

Why?

Because ordering matters within one escrow.

Example:

```text
EscrowCreated
TermsAccepted
FundingInitiated
EscrowFunded
DeliverySubmitted
DeliveryAccepted
EscrowReleased
```

All events for:

```text
escrow_123
```

should normally land in the same partition.

Different escrows can execute concurrently.

---

# 14. Escrow Commands

Primary commands include:

| Command               | Actor / Producer | Owner  |
| --------------------- | ---------------- | ------ |
| CreateEscrow          | Buyer/Seller API | Escrow |
| SubmitEscrow          | Creator          | Escrow |
| AcceptTerms           | Counterparty     | Escrow |
| RejectTerms           | Counterparty     | Escrow |
| CancelEscrow          | Participant      | Escrow |
| MarkFundingProcessing | Payment workflow | Escrow |
| ConfirmEscrowFunding  | Funding workflow | Escrow |
| StartFulfilment       | Seller           | Escrow |
| SubmitDelivery        | Seller           | Escrow |
| AcceptDelivery        | Buyer            | Escrow |
| OpenEscrowDispute     | Buyer/Seller     | Escrow |
| MarkReleasePending    | Release workflow | Escrow |
| ConfirmEscrowRelease  | Ledger workflow  | Escrow |
| ConfirmEscrowRefund   | Ledger workflow  | Escrow |

---

# 15. Escrow Events

| Event                | Producer       |
| -------------------- | -------------- |
| EscrowCreated        | Escrow Service |
| EscrowSubmitted      | Escrow Service |
| EscrowTermsChanged   | Escrow Service |
| EscrowTermsAccepted  | Escrow Service |
| EscrowTermsRejected  | Escrow Service |
| EscrowCancelled      | Escrow Service |
| EscrowExpired        | Escrow Service |
| EscrowFundingStarted | Escrow Service |
| EscrowFunded         | Escrow Service |
| FulfilmentStarted    | Escrow Service |
| DeliverySubmitted    | Escrow Service |
| InspectionStarted    | Escrow Service |
| DeliveryAccepted     | Escrow Service |
| EscrowDisputed       | Escrow Service |
| EscrowReleasePending | Escrow Service |
| EscrowReleased       | Escrow Service |
| EscrowRefundPending  | Escrow Service |
| EscrowRefunded       | Escrow Service |

---

# 16. Example: EscrowCreated

Topic:

```text
escrow.events.v1
```

Partition key:

```text
escrowId
```

Example payload:

```json
{
  "escrowId": "esc_123",
  "buyerId": "usr_100",
  "sellerId": "usr_200",
  "amountMinor": 100000,
  "currency": "NGN",
  "termsVersion": 1,
  "state": "AWAITING_COUNTERPARTY"
}
```

Consumers may include:

```text
Search Indexer
Risk Service
Audit Service
Analytics
Notification Service
```

---

# 17. Payment Topic

Topic:

```text
payment.events.v1
```

Producer:

```text
payment-service
```

Possible partition key:

```text
paymentId
```

or where escrow-level ordering is more valuable:

```text
escrowId
```

This choice must be based on consumer requirements.

For initial funding workflows:

```text
partition key = escrowId
```

is useful because multiple payment attempts belonging to the same escrow can remain ordered.

---

# 18. Payment Commands

| Command               | Producer          | Owner   |
| --------------------- | ----------------- | ------- |
| InitiatePayment       | API / Escrow flow | Payment |
| VerifyPayment         | Payment Service   | Payment |
| CancelPayment         | Buyer/System      | Payment |
| ReconcilePayment      | Reconciliation    | Payment |
| HandleProviderWebhook | Provider Adapter  | Payment |

---

# 19. Payment Events

| Event                 | Meaning                                |
| --------------------- | -------------------------------------- |
| PaymentInitiated      | Payment attempt created                |
| PaymentProcessing     | Provider processing                    |
| PaymentSucceeded      | Provider funding confirmed             |
| PaymentFailed         | Confirmed failure                      |
| PaymentOutcomeUnknown | Provider outcome uncertain             |
| PaymentCancelled      | Payment cancelled                      |
| PaymentReconciled     | State corrected through reconciliation |

---

# 20. PaymentSucceeded Event

Example:

```json
{
  "paymentId": "pay_123",
  "escrowId": "esc_123",
  "payerId": "usr_100",
  "amountMinor": 100000,
  "currency": "NGN",
  "provider": "provider-x",
  "providerReference": "abc123"
}
```

This event does not mean:

```text
Escrow is FUNDED
```

It means:

```text
External payment has been confirmed.
```

The Ledger still has to secure the funds internally.

This distinction is important.

---

# 21. Ledger Topic

Topic:

```text
ledger.events.v1
```

Producer:

```text
ledger-service
```

Partition key depends on the operation.

Possible keys:

```text
ledgerAccountId
escrowId
journalId
```

For escrow workflows, we will initially use:

```text
escrowId
```

where ordering of financial events for one escrow matters.

---

# 22. Ledger Commands

| Command              | Producer                  | Owner  |
| -------------------- | ------------------------- | ------ |
| SecureEscrowFunding  | Payment workflow          | Ledger |
| ReserveSellerBalance | Payout workflow           | Ledger |
| ReleaseEscrowFunds   | Escrow workflow           | Ledger |
| RefundEscrowFunds    | Escrow/Dispute workflow   | Ledger |
| ReverseJournal       | Finance operations        | Ledger |
| ReleaseReservation   | Payout workflow           | Ledger |
| PostAdjustment       | Controlled admin workflow | Ledger |

---

# 23. Ledger Events

| Event                     | Meaning                          |
| ------------------------- | -------------------------------- |
| EscrowFundingSecured      | Funding ledger journal committed |
| EscrowFundsReleased       | Release journal committed        |
| EscrowFundsRefunded       | Refund journal committed         |
| FundsReserved             | Balance reserved                 |
| ReservationReleased       | Reservation removed              |
| JournalReversed           | Existing journal reversed        |
| FinancialAdjustmentPosted | Controlled correction posted     |

---

# 24. EscrowFundingSecured

This is the event that proves internal financial accounting succeeded.

Flow:

```text
PaymentSucceeded
       ↓
SecureEscrowFunding
       ↓
Ledger transaction
       ↓
EscrowFundingSecured
```

Only after this event should the escrow become:

```text
FUNDED
```

---

# 25. Why Not Use PaymentSucceeded Directly?

Imagine:

```text
Payment Provider:
SUCCESS
```

but Ledger Service is unavailable.

If Escrow immediately enters:

```text
FUNDED
```

then Seller may start fulfilment even though the internal financial system has not recorded or secured the money.

Therefore:

```text
PaymentSucceeded
≠
EscrowFunded
```

Instead:

```text
PaymentSucceeded
      ↓
Ledger
      ↓
EscrowFundingSecured
      ↓
Escrow Service
      ↓
EscrowFunded
```

---

# 26. Payout Topic

Topic:

```text
payout.events.v1
```

Producer:

```text
payout-service
```

Partition key:

```text
payoutId
```

or:

```text
sellerId
```

if seller-level sequencing becomes necessary.

We will begin with:

```text
payoutId
```

---

# 27. Payout Events

```text
PayoutRequested
PayoutFundsReserved
PayoutProcessing
PayoutSucceeded
PayoutFailed
PayoutOutcomeUnknown
PayoutReconciled
```

---

# 28. Dispute Topic

Topic:

```text
dispute.events.v1
```

Producer:

```text
dispute-service
```

Partition key:

```text
escrowId
```

because dispute lifecycle relates strongly to a specific escrow.

Events include:

```text
DisputeOpened
DisputeEvidenceSubmitted
DisputeEscalated
DisputeResolvedForBuyer
DisputeResolvedForSeller
DisputeResolvedSplit
DisputeClosed
```

---

# 29. Identity Topic

Topic:

```text
identity.events.v1
```

Possible events:

```text
UserRegistered
UserEmailVerified
UserPhoneVerified
UserIdentityVerified
UserRestricted
UserUnrestricted
UserClosed
```

Consumers may include:

```text
Risk
Notification
Audit
Analytics
```

---

# 30. Risk Events

Risk decisions may produce:

```text
TransactionRiskApproved
TransactionRiskReviewRequired
TransactionRiskRejected
AccountRestricted
EscrowPlacedOnHold
PayoutBlocked
```

Risk event design must be treated carefully because some risk decisions are requests while others are facts.

For example:

```text
EvaluatePayoutRisk
```

is a command.

```text
PayoutRiskRejected
```

is an event.

---

# 31. Consumer Group Model

Suppose Kafka receives:

```text
EscrowReleased
```

Consumers include:

```text
search-indexer
notification-service
audit-service
analytics-service
risk-service
```

Each needs an independent consumer group.

Example:

```text
search-indexer-v1

notification-service-v1

audit-service-v1

analytics-service-v1
```

Kafka gives each consumer group its own logical view of the topic.

---

# 32. Horizontal Consumer Scaling

Suppose:

```text
escrow.events.v1
```

has:

```text
24 partitions
```

and Search Indexer has:

```text
6 consumer instances
```

Kafka may assign:

```text
4 partitions per consumer
```

If we increase to:

```text
12 consumers
```

approximately:

```text
2 partitions each
```

If we increase to:

```text
30 consumers
```

six consumers would have no partition to process.

Therefore:

```text
maximum active consumers in one group
≈
number of partitions
```

This is why partition count matters.

---

# 33. Partition Count Is Not Chosen Randomly

We will calculate Kafka partition counts later using:

```text
Required event throughput
÷
Measured throughput per partition
```

We must consider:

* Producer throughput
* Consumer throughput
* Message size
* Retention
* Broker capacity
* Ordering
* Expected future growth
* Rebalancing cost

We will not simply choose:

```text
100 partitions
```

because the system is "large."

---

# 34. Kafka Ordering Guarantee

Kafka provides ordering:

```text
within one partition
```

It does not provide global ordering across a topic.

For:

```text
escrowId = 123
```

all lifecycle events should use the same partition key.

Therefore:

```text
Created
Accepted
Funded
Delivered
Released
```

will remain ordered for that escrow.

But events for:

```text
escrow 123
```

and:

```text
escrow 999
```

may execute in parallel and have no meaningful global order.

That is desirable.

---

# 35. Hot Partition Problem

Suppose we partition by:

```text
marketplaceId
```

and one marketplace generates 40% of system traffic.

One Kafka partition could receive enormous traffic while others remain mostly idle.

This is a:

```text
hot partition
```

Partition keys must balance:

```text
ordering requirements
```

against:

```text
traffic distribution
```

---

# 36. Delivery Semantics

For business event processing we will generally assume:

```text
at-least-once delivery
```

This means:

```text
same event may be delivered more than once
```

Our consumers must therefore be idempotent.

We do not assume that Kafka magically gives exactly-once business execution.

---

# 37. Why Exactly-Once Is Harder Than Kafka Configuration

Suppose:

```text
Consumer receives EscrowFundingSecured
```

then:

```text
UPDATE escrow state = FUNDED
COMMIT
```

then the process crashes before committing the Kafka offset.

Kafka redelivers:

```text
EscrowFundingSecured
```

The consumer sees the event twice.

Even though Kafka provides sophisticated transactional features, our business database still needs idempotency.

---

# 38. Consumer Inbox

We will implement an inbox table.

Conceptually:

```sql
consumer_inbox
----------------
consumer_name
event_id
received_at
processed_at
```

Constraint:

```text
UNIQUE(consumer_name, event_id)
```

Processing:

```text
BEGIN

insert inbox(eventId)

if duplicate:
    ignore

apply business effect

COMMIT
```

This means a redelivered event does not create a second business effect.

---

# 39. Idempotent Consumer Example

Event:

```text
EscrowFundingSecured
eventId = evt_123
```

First delivery:

```text
insert evt_123
update escrow → FUNDED
commit
```

Second delivery:

```text
insert evt_123
```

fails unique check.

Consumer determines:

```text
already processed
```

and safely acknowledges the duplicate.

---

# 40. Aggregate Version Protection

Inbox deduplication protects against identical events.

Aggregate versioning additionally protects against stale events.

Suppose Elasticsearch receives:

```text
EscrowFunded
aggregateVersion = 10
```

then later receives an old:

```text
TermsAccepted
aggregateVersion = 7
```

It should not overwrite version 10 with version 7.

Projection logic should retain:

```text
latestAggregateVersion
```

and reject stale updates.

---

# 41. Producer Reliability

Services should not use:

```text
save database
then publish Kafka
```

without a reliability mechanism.

We will use:

```text
Transactional Outbox
```

Example:

```text
BEGIN

UPDATE escrow

INSERT outbox_event

COMMIT
```

An asynchronous publisher delivers the event to Kafka.

---

# 42. Outbox Structure

Initial conceptual table:

```text
outbox_events
-------------------------
id
aggregate_type
aggregate_id
aggregate_version
event_type
event_version
payload
correlation_id
causation_id
created_at
published_at
attempt_count
```

The table will later be refined.

---

# 43. Polling Publisher

Our first implementation may use a polling publisher.

Conceptually:

```text
SELECT unpublished outbox events
LIMIT 100
FOR UPDATE SKIP LOCKED
```

Then:

```text
publish to Kafka
```

and:

```text
mark as published
```

Multiple publisher replicas can work concurrently using:

```text
SKIP LOCKED
```

Later we will compare this with:

```text
Debezium CDC
```

---

# 44. Polling Versus CDC

## Polling outbox

Advantages:

* Easier to understand
* Fully controlled in application code
* Good learning path

Trade-offs:

* Polling overhead
* Additional publisher logic
* Marking delivery state

## Debezium CDC

Advantages:

* Reads database transaction log
* Low-latency event propagation
* Less application polling

Trade-offs:

* More operational infrastructure
* Schema and connector management
* Requires understanding CDC semantics

We will implement polling first and later migrate or prototype Debezium.

---

# 45. Outbox Duplicate Publication

Consider:

```text
Kafka publish succeeds
```

but:

```text
mark published fails
```

The publisher retries and sends the same event again.

Therefore:

```text
outbox does not eliminate duplicates
```

It prevents event loss.

Consumers must still be idempotent.

This gives us:

```text
At least once
+
idempotent consumers
=
effectively once business effect
```

---

# 46. Retry Classification

Not every failure should be retried.

## Retryable

Examples:

```text
temporary database outage
Kafka broker unavailable
network timeout
HTTP 503
temporary Elasticsearch rejection
```

## Usually non-retryable

Examples:

```text
invalid event schema
unsupported currency
invalid state transition
malformed payload
authorization failure
business constraint violation
```

Retrying permanent failures only creates additional load.

---

# 47. Kafka Consumer Retry Strategy

We may later implement:

```text
main topic
   ↓
retry topic
   ↓
retry topic 2
   ↓
dead-letter topic
```

Example:

```text
escrow.events.v1

escrow.events.retry.5s.v1

escrow.events.retry.1m.v1

escrow.events.dlt.v1
```

The exact design will depend on Spring Kafka and operational needs.

---

# 48. Dead-Letter Topics

A dead-letter topic contains messages that could not be processed after bounded retries.

A DLT is not a rubbish bin.

Every DLT must have:

* Monitoring
* Alerting
* Investigation process
* Replay procedure
* Ownership
* Retention policy

Example:

```text
ledger.events.dlt.v1
```

may represent a serious incident.

---

# 49. Poison Messages

Suppose one event repeatedly crashes a consumer.

Without isolation:

```text
Partition
   ↓
bad event
   ↓
consumer keeps failing
   ↓
all later events blocked
```

Retry and dead-letter policies must eventually isolate such poison messages.

But skipping events can also violate ordering.

Therefore the decision depends on the domain.

---

# 50. Ordering Versus Dead-Lettering

Suppose:

```text
EscrowFunded version 4
```

cannot be processed.

Later:

```text
DeliverySubmitted version 5
```

arrives.

Simply moving version 4 to a DLT and processing version 5 may create an invalid projection.

For state-sensitive consumers, we may need:

```text
pause partition
```

or:

```text
reconcile authoritative state
```

rather than blindly skipping failed events.

This trade-off will be tested later.

---

# 51. Event Schema Evolution

Events must change safely.

Bad approach:

```text
rename field
remove old field
deploy producer
hope consumers work
```

Better:

```text
add backward-compatible field
deploy consumers
deploy producer
deprecate old field later
```

Event contracts must be treated like APIs.

---

# 52. Event Versioning

Example:

```text
payment.succeeded
version = 1
```

Later, requirements change significantly.

We may publish:

```text
payment.succeeded
version = 2
```

Consumers must declare supported versions.

We should not create a new version for every optional field addition if backward compatibility remains intact.

---

# 53. Serialization

Initial development may use:

```text
JSON
```

because it is:

* Human-readable
* Easy to inspect
* Easy to debug
* Familiar

At higher maturity we will compare:

```text
JSON
Avro
Protobuf
```

using criteria such as:

* Payload size
* Schema enforcement
* Compatibility
* Developer ergonomics
* Schema registry support
* Cross-language consumers

---

# 54. Schema Registry

When we introduce Avro or Protobuf, we may also introduce a schema registry.

It can enforce compatibility policies such as:

```text
BACKWARD
FORWARD
FULL
```

This becomes important when dozens of independently deployed consumers depend on event contracts.

---

# 55. Sensitive Information in Events

Events must not become a data-leak mechanism.

Avoid unnecessary:

```text
passwords
access tokens
API secrets
full bank account details
identity documents
card information
private messages
```

Consumers should receive only the fields required for their function.

---

# 56. Event Size

Events should generally remain small.

Avoid:

```text
EscrowCreated
{
    entire user object,
    entire transaction history,
    every message,
    every document
}
```

Large messages increase:

* Network bandwidth
* Kafka storage
* Replication cost
* Consumer memory usage
* Serialization cost

Instead publish relevant facts and stable identifiers.

---

# 57. Event Notification Versus Event-Carried State

We will learn two patterns.

## Event notification

```json
{
  "escrowId": "esc_123"
}
```

Consumer must fetch more data.

Advantages:

* Small event.

Trade-off:

* Creates synchronous coupling and additional traffic.

## Event-carried state transfer

```json
{
  "escrowId": "esc_123",
  "state": "FUNDED",
  "amountMinor": 100000,
  "currency": "NGN"
}
```

Advantages:

* Consumer can update projection without calling producer.

Trade-off:

* More duplicated data.
* Schema evolution matters more.

We will choose intentionally per consumer.

---

# 58. RabbitMQ Is Not the Domain Event Backbone

RabbitMQ will primarily carry commands/jobs such as:

```text
SendEmail
GenerateReceipt
ScanEvidence
RetryPayoutProviderRequest
```

These differ from Kafka domain facts.

Kafka:

```text
EscrowReleased
```

RabbitMQ:

```text
GenerateEscrowReleaseReceipt
```

---

# 59. RabbitMQ Job Envelope

Conceptual job:

```json
{
  "jobId": "job_123",
  "jobType": "GenerateReceipt",
  "correlationId": "cor_456",
  "createdAt": "...",
  "attempt": 1,
  "payload": {
    "escrowId": "esc_123"
  }
}
```

Workers must also be idempotent.

---

# 60. RabbitMQ Worker Failure Example

Worker:

```text
receives SendEmail
```

Then:

```text
email provider accepts message
```

but worker crashes before:

```text
ACK
```

RabbitMQ redelivers.

Without idempotency:

```text
user may receive duplicate email
```

For some jobs this is acceptable.

For others, we need deduplication.

---

# 61. RabbitMQ Queues

Initial future queues may include:

```text
notification.email.queue
notification.sms.queue
notification.push.queue

receipt.generate.queue

evidence.scan.queue

payout.retry.queue
```

We will define exchanges and routing keys when RabbitMQ enters implementation.

---

# 62. Command Delivery

Not all commands need to go through Kafka or RabbitMQ.

User-facing commands typically arrive synchronously:

```text
HTTP request
     ↓
Escrow Service
```

Example:

```text
POST /escrows/{id}/accept-delivery
```

becomes internally:

```text
AcceptDelivery command
```

Kafka is not necessary for that initial hop.

---

# 63. Asynchronous Commands

Some commands may be sent asynchronously.

Example:

```text
Escrow Service
    ↓
ReleaseEscrowFunds
    ↓
Ledger Service
```

This can be implemented through messaging because:

* The caller does not require immediate final completion.
* Ledger may be temporarily unavailable.
* Work must remain durable.

We will decide whether such cross-service commands use:

```text
Kafka command topic
```

or another mechanism based on requirements.

We must not use Kafka exclusively for events merely because Kafka is present.

---

# 64. First Workflow Catalogue: Create Escrow

Command:

```text
CreateEscrow
```

Owner:

```text
Escrow Service
```

Database transaction:

```text
INSERT escrow
INSERT terms
INSERT outbox event
```

Event:

```text
EscrowCreated
```

Kafka:

```text
escrow.events.v1
```

Partition key:

```text
escrowId
```

Consumers:

```text
Risk
Search
Audit
Analytics
Notification
```

---

# 65. Funding Workflow Catalogue

Step 1:

```text
InitiatePayment
```

Owner:

```text
Payment Service
```

Event:

```text
PaymentInitiated
```

Step 2:

Provider eventually confirms:

```text
PaymentSucceeded
```

Step 3:

Ledger receives funding request:

```text
SecureEscrowFunding
```

Step 4:

Ledger commits:

```text
EscrowFundingSecured
```

Step 5:

Escrow consumes and transitions:

```text
FUNDING_PROCESSING
→ FUNDED
```

Escrow publishes:

```text
EscrowFunded
```

---

# 66. Release Workflow Catalogue

Buyer command:

```text
AcceptDelivery
```

Escrow event:

```text
DeliveryAccepted
```

Escrow transitions:

```text
INSPECTION
→ RELEASE_PENDING
```

Financial command:

```text
ReleaseEscrowFunds
```

Ledger commits journal.

Ledger event:

```text
EscrowFundsReleased
```

Escrow consumes.

Escrow transitions:

```text
RELEASE_PENDING
→ RELEASED
```

Escrow event:

```text
EscrowReleased
```

Downstream:

```text
Search
Notification
Audit
Payout
Analytics
```

---

# 67. Dispute Workflow Catalogue

Command:

```text
OpenDispute
```

Dispute Service publishes:

```text
DisputeOpened
```

Escrow consumes:

```text
DisputeOpened
```

and transitions to:

```text
DISPUTED
```

Resolution command:

```text
ResolveDispute
```

Possible events:

```text
DisputeResolvedForBuyer
DisputeResolvedForSeller
DisputeResolvedSplit
```

These drive refund or release workflows.

---

# 68. Event Consumer Ownership

Consumers should subscribe only to events they need.

Bad:

```text
every service subscribes to every topic
```

This creates unnecessary coupling.

Good:

```text
search-indexer:
escrow.events
payment.events
dispute.events
```

while:

```text
notification-service:
selected escrow events
selected payout events
selected identity events
```

---

# 69. Event Contract Ownership

The publishing service owns its event contracts.

For example:

```text
payment-service
```

owns:

```text
PaymentSucceeded
```

Escrow Service may consume it but cannot arbitrarily change its schema.

Changes require coordination and compatibility testing.

---

# 70. Event Contract Repository

We will eventually maintain contracts under:

```text
contracts/events/
```

Possible structure:

```text
contracts/
└── events/
    ├── escrow/
    │   ├── escrow-created-v1.json
    │   └── escrow-funded-v1.json
    │
    ├── payment/
    │   └── payment-succeeded-v1.json
    │
    └── ledger/
        └── escrow-funding-secured-v1.json
```

If we adopt Avro:

```text
.avsc
```

If we adopt Protobuf:

```text
.proto
```

---

# 71. Event Contract Testing

CI should eventually verify:

* Event schema validity
* Backward compatibility
* Producer serialization
* Consumer deserialization
* Required fields
* Contract changes

This prevents one team from breaking multiple downstream consumers.

---

# 72. Event Replay

Kafka's durable history enables replay.

Possible uses:

```text
Rebuild Elasticsearch

Recalculate analytics

Rebuild risk projections

Recover a failed downstream consumer
```

Consumers must therefore be designed so replay does not create duplicate irreversible operations.

For example:

```text
replaying EscrowReleased
```

must not pay the seller again.

---

# 73. Replay-Safe Consumers

Good replay targets:

```text
Search projections
Analytics
Audit projections
Read models
```

Financial consumers require stronger protection.

Example:

```text
EscrowFundsReleased
```

replay should be ignored if already processed.

---

# 74. Retention

Retention should be set by topic purpose.

Example:

```text
escrow.events.v1
→ weeks or months

payment.events.v1
→ appropriate financial retention

retry topics
→ hours/days

DLT
→ until resolved

compacted reference topics
→ latest state retained
```

Kafka is not necessarily our permanent legal archive.

Long-term events may be archived to object storage.

---

# 75. Topic Compaction

Some future topics may use log compaction.

Example:

```text
user-risk-profile
```

where consumers primarily need the latest value for each key.

Compaction does not mean immediate deletion of old values.

We will study its behaviour when we implement Kafka administration.

---

# 76. Backpressure

Kafka consumers can fall behind.

Example:

```text
producer:
100,000 events/sec

consumer:
60,000 events/sec
```

Lag grows by:

```text
40,000 events/sec
```

This is observable through:

```text
consumer lag
```

The solution may include:

* More consumer instances
* More partitions
* Faster processing
* Batch processing
* Downstream optimisation
* Load shedding

Not simply:

```text
increase thread count indefinitely
```

---

# 77. Consumer Lag as an Autoscaling Signal

For event-driven services, CPU may remain low while Kafka lag grows.

Therefore Kubernetes autoscaling may eventually use:

```text
Kafka consumer lag
```

Example:

```text
lag < 10,000
→ 5 pods

lag > 100,000
→ scale to 20 pods
```

The actual policy will be load-tested.

---

# 78. Event Processing Time

Every consumer should expose metrics such as:

```text
events_processed_total

event_processing_duration

consumer_lag

duplicate_events_total

event_failures_total

dlt_events_total
```

Without these metrics, event-driven systems become difficult to operate.

---

# 79. Kafka Failure

Suppose Kafka becomes unavailable.

Escrow Service performs:

```text
CreateEscrow
```

Local transaction commits:

```text
escrow
+
outbox
```

Kafka publish fails.

The request does not need to roll back if the business operation itself was successfully committed.

The outbox retains:

```text
EscrowCreated
```

The publisher retries when Kafka returns.

---

# 80. Consumer Failure

Suppose Search Indexer is unavailable for one hour.

Kafka retains events.

When Search Indexer restarts:

```text
consumer offset
```

shows where processing stopped.

It resumes from that point.

Search may be stale temporarily, but escrow transactions continue.

---

# 81. Broker Is Not Source of Financial Truth

Even though Kafka is durable, the source of financial truth remains:

```text
Ledger database
```

Kafka is the distribution mechanism for financial facts.

If necessary, critical events can be reconstructed from authoritative databases and audit history.

---

# 82. Message Ordering Strategy

We will use:

```text
aggregateId
```

as the default partition key for aggregate lifecycle events.

Examples:

```text
Escrow event
→ escrowId

Payment event
→ paymentId or escrowId

Dispute event
→ escrowId

Payout event
→ payoutId
```

Ledger requires deeper analysis because account-level ordering and escrow-level ordering may conflict.

We will design it separately.

---

# 83. Ledger Ordering Problem

Imagine one seller account receives releases from:

```text
Escrow A
Escrow B
Escrow C
```

If events are partitioned by:

```text
escrowId
```

these operations may execute concurrently.

That is good for throughput.

But the ledger must still correctly serialize conflicting writes to:

```text
seller account
```

Kafka partitioning alone cannot guarantee ledger correctness.

The database/account concurrency model must protect the invariant.

---

# 84. Kafka Is Not a Database Lock

We will never rely solely on:

```text
same Kafka partition
```

to prevent financial races.

Why?

Because financial commands may also arrive through:

* HTTP
* Replay
* Reconciliation
* Administrative workflows
* Another topic

The authoritative database must enforce the financial invariant.

---

# 85. Command IDs

Every important command should have:

```text
commandId
```

This helps:

* Trace retries
* Connect command and event
* Implement idempotency
* Audit workflows

Example:

```json
{
  "commandId": "cmd_123",
  "commandType": "ReleaseEscrowFunds",
  "correlationId": "cor_456",
  "causationId": "evt_789"
}
```

---

# 86. Idempotency Key Versus Command ID

These are related but different.

## Idempotency key

Usually supplied by caller:

```text
release-request-998
```

Prevents duplicate business execution.

## Command ID

Unique identity of the internal command message.

A retry may intentionally preserve the same command ID or link through causation depending on implementation.

We will model this precisely during API design.

---

# 87. Event Naming Convention

We will use business names rather than technical implementation names.

Good:

```text
EscrowFunded
PaymentSucceeded
FundsReleased
DisputeOpened
```

Avoid:

```text
EscrowRowUpdated
PaymentDatabaseInserted
LedgerMethodFinished
```

Events should communicate business meaning.

---

# 88. Topic Naming Convention

Initial convention:

```text
<domain>.events.v<major-version>
```

Examples:

```text
escrow.events.v1
payment.events.v1
ledger.events.v1
payout.events.v1
dispute.events.v1
```

Retry and DLT names:

```text
<topic>.retry.<delay>

<topic>.dlt
```

Exact names will be standardised in an ADR.

---

# 89. Environment Separation

Development, staging, and production must not accidentally share event streams.

Environment separation may be provided through:

* Separate Kafka clusters
* Namespace/topic prefix
* Infrastructure-level isolation

Production financial streams should preferably be isolated from non-production infrastructure.

---

# 90. Initial Kafka Learning Scope

Our first Kafka implementation will deliberately focus on:

```text
PaymentSucceeded
       ↓
Ledger consumer
       ↓
EscrowFundingSecured
       ↓
Escrow consumer
       ↓
EscrowFunded
```

Through this single flow we will learn:

* Producers
* Consumers
* Topics
* Partitions
* Keys
* Consumer groups
* Offsets
* Outbox
* Inbox
* Duplicate delivery
* Ordering
* Retries
* DLT
* Consumer lag
* Event versioning
* Tracing

We do not need 50 event types to learn Kafka properly.

---

# 91. Initial RabbitMQ Learning Scope

RabbitMQ will enter when:

```text
EscrowFunded
```

needs to cause user notifications.

Flow:

```text
EscrowFunded
      ↓ Kafka
Notification Service
      ↓
Create notification
      ↓
RabbitMQ
      ↓
Email Worker
```

This will let us directly compare:

```text
Kafka domain event
```

with:

```text
RabbitMQ work queue
```

inside the same business flow.

---

# 92. Event-Driven Architecture Rule

The system should not become:

```text
event-driven everything
```

We will use:

```text
synchronous communication
```

when an immediate authoritative answer is needed.

We will use:

```text
Kafka
```

when durable business facts need distribution.

We will use:

```text
RabbitMQ
```

when background work must be performed.

The architecture is hybrid by design.

---

# 93. Initial Event Matrix

| Producer | Event                | Topic              | Partition Key | Primary Consumers               |
| -------- | -------------------- | ------------------ | ------------- | ------------------------------- |
| Identity | UserRegistered       | identity.events.v1 | userId        | Risk, Notification, Audit       |
| Escrow   | EscrowCreated        | escrow.events.v1   | escrowId      | Search, Risk, Audit             |
| Escrow   | EscrowTermsAccepted  | escrow.events.v1   | escrowId      | Audit, Notification             |
| Payment  | PaymentSucceeded     | payment.events.v1  | escrowId      | Ledger, Audit, Risk             |
| Ledger   | EscrowFundingSecured | ledger.events.v1   | escrowId      | Escrow, Audit                   |
| Escrow   | EscrowFunded         | escrow.events.v1   | escrowId      | Search, Notification, Risk      |
| Escrow   | DeliverySubmitted    | escrow.events.v1   | escrowId      | Search, Notification            |
| Escrow   | DeliveryAccepted     | escrow.events.v1   | escrowId      | Audit                           |
| Ledger   | EscrowFundsReleased  | ledger.events.v1   | escrowId      | Escrow, Payout, Audit           |
| Escrow   | EscrowReleased       | escrow.events.v1   | escrowId      | Search, Notification, Analytics |
| Dispute  | DisputeOpened        | dispute.events.v1  | escrowId      | Escrow, Risk, Audit             |
| Payout   | PayoutSucceeded      | payout.events.v1   | payoutId      | Notification, Audit             |

---

# 94. Reliability Matrix

| Problem                            | Protection                            |
| ---------------------------------- | ------------------------------------- |
| DB committed but Kafka unavailable | Transactional outbox                  |
| Event published twice              | Idempotent consumer                   |
| Consumer crashes after DB commit   | Inbox/deduplication                   |
| Events arrive out of order         | Aggregate version                     |
| Consumer permanently fails         | Retry + DLT                           |
| Consumer falls behind              | Lag monitoring + scaling              |
| Schema changes                     | Versioned contracts                   |
| Poison event blocks processing     | Domain-specific retry strategy        |
| Kafka unavailable                  | Outbox backlog                        |
| Search consumer unavailable        | Replay from offset                    |
| Notification unavailable           | Kafka retention + RabbitMQ durability |

---

# 95. Key Learning Principles

Throughout implementation we must be able to answer:

```text
Why is this a command?

Why is this an event?

Who owns it?

Which service produces it?

Which services consume it?

Does ordering matter?

What is the partition key?

Can it be delivered twice?

What happens if it is?

What happens if the consumer crashes?

What happens if Kafka is unavailable?

Can the event be replayed?

Will replay accidentally move money?

How does the schema evolve?

How will we observe failures?
```

If we cannot answer these questions, the event architecture is incomplete.

---

# 96. Next Architecture Decision

The next document should define the **data and ledger architecture**.

This will cover:

```text
PostgreSQL ownership
Database per service
Schema boundaries
Primary keys
UUIDv7
Ledger journals
Ledger entries
Available balance
Held balance
Reservations
Indexes
Optimistic locking
Pessimistic locking
Isolation levels
Concurrent spending
Table partitioning
Read replicas
Sharding
HikariCP
PgBouncer
Connection budgets
Database failure
Reconciliation
```

This is where we start going deeply into the database engineering required for large financial systems.
