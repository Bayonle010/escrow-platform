# Escrow Platform Data and Ledger Architecture

**Version:** 1.0
**Status:** Draft
**System:** General-Purpose Escrow Platform
**Primary Database:** PostgreSQL
**Financial Model:** Immutable Double-Entry Ledger

---

# 1. Purpose

This document defines how authoritative transactional data and financial records are stored, updated, scaled, and protected.

The architecture must support:

* Hundreds of millions of users
* Hundreds of millions to billions of escrow records
* Billions of ledger entries
* High concurrent write volume
* Large read volume
* Duplicate requests
* Concurrent spending attempts
* Database failover
* Service scaling
* Provider reconciliation

The main design goal is not merely high throughput.

The main goal is:

```text
high throughput
+
financial correctness
+
durability
+
auditability
```

---

# 2. Core Data Principle

Every important data element must have one authoritative owner.

Examples:

```text
User identity
→ Identity Service

Escrow lifecycle
→ Escrow Service

Payment attempts
→ Payment Service

Financial balances
→ Ledger Service

Payout state
→ Payout Service

Dispute state
→ Dispute Service
```

No service may directly modify another service's authoritative database.

---

# 3. Initial PostgreSQL Topology

During development, multiple logical databases may run on one PostgreSQL instance:

```text
PostgreSQL

├── identity_db
├── escrow_db
├── payment_db
├── ledger_db
├── payout_db
└── dispute_db
```

This is a development convenience.

The code must not assume these databases share one physical server.

At higher scale:

```text
Identity PostgreSQL Cluster

Escrow PostgreSQL Cluster

Payment PostgreSQL Cluster

Ledger PostgreSQL Cluster

Payout PostgreSQL Cluster
```

The Ledger database should eventually receive especially strong physical isolation.

---

# 4. Why Database Ownership Matters

Consider:

```text
escrow-service
```

directly updating:

```text
ledger_db
```

This creates several problems:

* Ledger rules can be bypassed.
* Security boundaries disappear.
* Schema changes become dangerous.
* Services become tightly coupled.
* Auditability becomes weaker.
* Independent deployment becomes harder.

Correct approach:

```text
Escrow Service
      ↓
Ledger command/API
      ↓
Ledger Service
      ↓
Ledger Database
```

---

# 5. Database Transactions Stay Local

An ordinary ACID transaction must normally remain inside one service-owned database.

We do not attempt:

```text
BEGIN

update escrow_db

update ledger_db

update payment_db

COMMIT
```

as one application transaction.

Instead:

```text
local ACID transaction
+
transactional outbox
+
Kafka
+
idempotent consumers
+
reconciliation
```

coordinates distributed workflows.

---

# 6. Primary Identifier Strategy

Primary business records should use globally unique identifiers.

Recommended default:

```text
UUIDv7
```

Reasons include:

* Globally unique generation
* No central sequence generator
* Suitable for distributed services
* Roughly time ordered
* Better B-tree locality than purely random UUIDv4

Examples:

```text
user_id
escrow_id
payment_id
journal_id
payout_id
event_id
```

Internal high-volume tables may later use alternative surrogate keys where benchmarks justify them.

---

# 7. UUID Storage

In PostgreSQL, UUID identifiers should use:

```sql
UUID
```

rather than:

```sql
VARCHAR(36)
```

Benefits include:

* Smaller storage
* Native validation
* Better indexing
* Clearer schema semantics

---

# 8. Time Representation

Authoritative timestamps should use:

```sql
TIMESTAMPTZ
```

and be stored in UTC.

Examples:

```text
created_at
updated_at
occurred_at
processed_at
```

Business-specific local time zones may be stored separately when required.

---

# 9. Money Representation

Monetary values must not use floating-point types.

Avoid:

```java
double
float
```

Preferred database representation:

```sql
BIGINT
```

containing minor currency units.

Example:

```text
NGN 100.50
```

may be represented as:

```text
10050 minor units
```

A monetary value therefore requires:

```text
amount_minor
currency
```

---

# 10. Currency Representation

Currency should use an explicit ISO-style code.

Example:

```text
NGN
USD
GBP
EUR
```

Database representation may initially use:

```sql
CHAR(3)
```

or a constrained domain type.

No amount exists without a currency.

---

# 11. Ledger Design Goal

The Ledger Service must answer:

```text
Where is the money?
```

and:

```text
How did it move?
```

It must not depend on mutable balance records as its only history.

The financial source of truth is:

```text
immutable ledger entries
```

---

# 12. Double-Entry Accounting

Every financial operation creates a journal.

Every journal contains at least two entries.

Rule:

```text
total debits
=
total credits
```

Example:

Escrow releases 10000 units.

Platform fee is 200 units.

```text
Debit:
Escrow Held                 10000

Credit:
Seller Available             9800

Credit:
Platform Revenue              200
```

Therefore:

```text
10000 debit
=
10000 credit
```

---

# 13. Ledger Account Model

Conceptual table:

```text
ledger_accounts
--------------------------------
id
owner_type
owner_id
account_type
currency
status
created_at
version
```

Examples of `owner_type`:

```text
USER
ESCROW
PLATFORM
PROVIDER
SYSTEM
```

Examples of `account_type`:

```text
ESCROW_HELD

SELLER_AVAILABLE

USER_AVAILABLE

PAYOUT_RESERVED

PROVIDER_CLEARING

REFUND_PAYABLE

PLATFORM_REVENUE

SUSPENSE
```

---

# 14. One Currency Per Ledger Account

A ledger account must belong to one currency.

Example:

```text
seller_123_available_NGN
```

must never contain:

```text
USD entries
```

If the same seller uses USD:

```text
seller_123_available_USD
```

is a separate account.

This greatly simplifies correctness.

---

# 15. Journal Model

Conceptual table:

```text
ledger_journals
--------------------------------
id
business_reference
journal_type
currency
status
correlation_id
causation_id
created_at
```

Example `journal_type` values:

```text
ESCROW_FUNDING

ESCROW_RELEASE

ESCROW_REFUND

PAYOUT_RESERVATION

PAYOUT_COMPLETION

PAYOUT_RELEASE

REVERSAL

ADJUSTMENT
```

---

# 16. Business Reference

Every financial journal must have a unique business reference.

Examples:

```text
FUNDING:pay_123

RELEASE:esc_123:1

REFUND:refund_123

PAYOUT:payout_123

REVERSAL:journal_123
```

Database constraint:

```text
UNIQUE(business_reference)
```

This is one of the protections against duplicate financial execution.

---

# 17. Ledger Entry Model

Conceptual table:

```text
ledger_entries
--------------------------------
id
journal_id
account_id
direction
amount_minor
currency
sequence
created_at
```

Direction:

```text
DEBIT
CREDIT
```

Every entry must satisfy:

```text
amount_minor > 0
```

A negative amount is not required.

Direction expresses the accounting movement.

---

# 18. Journal Atomicity

A journal is one financial transaction.

All its entries must commit together.

Example:

```text
BEGIN

INSERT ledger_journal

INSERT debit entry

INSERT seller credit

INSERT platform fee credit

COMMIT
```

If any entry fails:

```text
ROLLBACK
```

A partial journal must never exist.

---

# 19. Journal Balance Validation

Before commit:

```text
sum(debit amounts)
=
sum(credit amounts)
```

The application must validate this.

For additional protection, the architecture may later use:

* Deferred constraints
* Stored procedures
* Database triggers
* Journal finalization states

The final enforcement mechanism will be benchmarked.

---

# 20. Ledger Entries Are Immutable

After commit:

```text
UPDATE ledger_entries
```

should never be part of an ordinary financial workflow.

Similarly:

```text
DELETE FROM ledger_entries
```

must not be used to correct history.

Correction happens through another journal.

---

# 21. Reversal

Suppose journal:

```text
J100
```

incorrectly moved:

```text
10000
```

A reversal creates:

```text
J101
```

with opposite entries.

The original journal remains.

Conceptually:

```text
J101.reverses_journal_id = J100
```

This preserves the audit trail.

---

# 22. Balance Types

A user may see several financial concepts.

They must not be treated as one number.

Examples:

```text
ledger balance

available balance

held balance

reserved balance

pending payout balance
```

---

# 23. Ledger Balance

Ledger balance is derived from ledger entries.

Conceptually:

```text
credits - debits
```

or the inverse depending on account type.

The exact formula depends on account classification.

---

# 24. Available Balance

Available balance means:

```text
money that can currently be used
```

It may be less than ledger balance because some funds are:

* Reserved
* Frozen
* Pending
* Disputed
* Restricted

---

# 25. Held Balance

Escrow funding creates money held for a transaction.

Example:

```text
Escrow account:
ESCROW_HELD
```

This money cannot be freely withdrawn by either party.

---

# 26. Reserved Balance

Before a payout:

```text
Seller Available
```

may be moved into:

```text
Payout Reserved
```

This prevents two concurrent payout requests from spending the same funds.

---

# 27. Why One Mutable Balance Column Is Dangerous

A simplistic model:

```text
wallet
----------------
user_id
balance
```

and:

```sql
UPDATE wallet
SET balance = balance - 100;
```

cannot by itself explain:

* Why the balance changed
* Which escrow caused it
* Whether the change was reversed
* Which provider was involved
* Whether the operation duplicated

A ledger provides history and accountability.

---

# 28. Materialized Balance

Calculating:

```text
SUM(all ledger entries)
```

for every balance request becomes expensive at scale.

Therefore we may maintain:

```text
ledger_account_balances
```

Example:

```text
account_id
posted_balance_minor
available_balance_minor
version
updated_at
```

This is a performance projection.

It must remain reconcilable with immutable entries.

---

# 29. Balance Update Transaction

When posting a journal:

```text
BEGIN
```

may include:

```text
insert journal

insert entries

update account balance projections

insert outbox event
```

Then:

```text
COMMIT
```

These records belong to the same Ledger database and may therefore share one ACID transaction.

---

# 30. Financial Command Idempotency

Suppose Ledger receives:

```text
ReleaseEscrowFunds
```

three times.

The database must guarantee one business effect.

Protection layers include:

```text
unique journal business_reference

consumer inbox event deduplication

command idempotency record
```

Critical correctness should not depend on Redis alone.

---

# 31. Idempotency Database Table

Conceptual table:

```text
idempotency_records
--------------------------------
id
scope
idempotency_key
request_hash
status
resource_id
response_code
response_body
created_at
expires_at
```

Constraint:

```text
UNIQUE(scope, idempotency_key)
```

---

# 32. Idempotency Request Hash

Suppose caller sends:

```text
Idempotency-Key: release-123
```

Request A:

```text
amount = 10000
```

Request B:

```text
amount = 20000
```

The second request must not receive the cached result of the first.

A request fingerprint allows detection of incompatible reuse.

---

# 33. Redis and Idempotency

Redis may accelerate idempotency lookups.

But authoritative critical idempotency records should be durable.

Correct relationship:

```text
PostgreSQL
= durable business protection

Redis
= optional acceleration
```

---

# 34. Concurrent Spending Problem

Suppose seller has:

```text
available = 10000
```

Two payout requests arrive simultaneously:

```text
Payout A = 8000

Payout B = 8000
```

Without concurrency control, both may read:

```text
available = 10000
```

and both attempt to succeed.

Result:

```text
16000 spent from 10000
```

This must be impossible.

---

# 35. Concurrency Strategy Options

Possible strategies include:

```text
Pessimistic row locking

Optimistic locking

Atomic conditional update

Serializable transactions

Single-writer partition processing
```

Different operations may use different strategies.

---

# 36. Pessimistic Locking

Example:

```sql
SELECT *
FROM ledger_account_balances
WHERE account_id = ?
FOR UPDATE;
```

This locks the account row until transaction completion.

Then:

```text
check available balance

post journal

update projection

commit
```

Advantages:

* Straightforward correctness

Trade-offs:

* Lock waits
* Lower throughput for hot accounts
* Deadlock risk

---

# 37. Optimistic Locking

Example:

```text
account_id = A
available_balance = 10000
version = 15
```

Update:

```sql
UPDATE ledger_account_balances
SET available_balance_minor = 2000,
    version = 16
WHERE account_id = ?
  AND version = 15;
```

If affected rows:

```text
0
```

another transaction won.

The request must retry or fail safely.

---

# 38. Atomic Conditional Update

Another option:

```sql
UPDATE ledger_account_balances
SET available_balance_minor =
    available_balance_minor - 8000
WHERE account_id = ?
  AND available_balance_minor >= 8000;
```

If:

```text
row count = 1
```

reservation succeeded.

If:

```text
row count = 0
```

funds were insufficient or changed concurrently.

This can be extremely effective for certain balance-reservation workloads.

---

# 39. Database Is the Final Concurrency Guard

Distributed locks may reduce contention.

They must not be the only protection.

Why?

Redis lock may:

* Expire unexpectedly
* Lose leadership
* Be unavailable
* Experience network partitions

The authoritative PostgreSQL transaction must still prevent invalid financial state.

---

# 40. Distributed Lock Use Cases

Redis-based coordination may still be useful for:

* Preventing duplicate expensive computation
* Leader election for non-critical jobs
* Reducing duplicate scheduled execution
* Cache regeneration

It should not replace:

```text
unique constraints

row locks

atomic database updates

transaction isolation
```

for financial correctness.

---

# 41. Transaction Isolation

PostgreSQL isolation levels include:

```text
READ COMMITTED

REPEATABLE READ

SERIALIZABLE
```

We will not globally switch the entire platform to `SERIALIZABLE`.

Different workloads have different requirements.

---

# 42. READ COMMITTED

PostgreSQL default.

Suitable for many ordinary application transactions.

However, developers must understand:

```text
two statements may observe different committed states
```

and business invariants may still require explicit locking.

---

# 43. REPEATABLE READ

Provides a consistent transaction snapshot.

Useful for some workflows.

But snapshot consistency does not automatically prevent every business-level write conflict.

---

# 44. SERIALIZABLE

Provides the strongest isolation.

Transactions behave as if executed serially.

But PostgreSQL may abort transactions with serialization failures.

Applications must safely retry.

Trade-offs:

* More aborted transactions
* Higher contention cost
* More complex retry behaviour

We will benchmark it for specific ledger operations rather than enabling it everywhere.

---

# 45. Initial Ledger Isolation Direction

Initial design:

```text
READ COMMITTED
+
explicit row/conditional locking
+
unique business constraints
+
short transactions
```

for most ledger operations.

Selected operations may later use stronger isolation after testing.

---

# 46. Transaction Duration

Financial database transactions should be short.

Never do:

```text
BEGIN

lock financial row

call payment provider over network

wait 8 seconds

update rows

COMMIT
```

Remote network calls must not normally happen while holding database locks.

---

# 47. Correct Provider Pattern

Instead:

```text
Transaction 1:
reserve internal funds
commit
```

Then:

```text
call provider
```

Then:

```text
Transaction 2:
finalize based on verified result
```

This is why financial workflows often require explicit intermediate states.

---

# 48. Payout Reservation Example

Seller requests payout:

```text
Seller Available
=
10000
```

Request:

```text
8000
```

Ledger transaction:

```text
Debit:
Seller Available      8000

Credit:
Payout Reserved       8000
```

Now:

```text
Seller Available = 2000
Payout Reserved = 8000
```

External payout begins only after reservation succeeds.

---

# 49. Payout Success

When provider confirms payout:

```text
Debit:
Payout Reserved

Credit:
Provider/Bank Clearing
```

depending on accounting design.

The reservation is consumed.

---

# 50. Payout Failure

If provider definitively fails:

```text
Debit:
Payout Reserved

Credit:
Seller Available
```

This returns reserved funds.

But if outcome is:

```text
UNKNOWN
```

the reservation must remain until reconciliation establishes truth.

---

# 51. Escrow Funding Journal

A payment provider confirms funding.

Example journal:

```text
Debit:
Provider Clearing           10000

Credit:
Escrow Held                 10000
```

Actual debit/credit orientation will follow the final chart-of-accounts accounting model, but the journal must balance.

---

# 52. Escrow Release Journal

Example:

```text
Debit:
Escrow Held                 10000

Credit:
Seller Available             9800

Credit:
Platform Revenue              200
```

One atomic journal.

---

# 53. Refund Journal

Example:

```text
Debit:
Escrow Held                 10000

Credit:
Refund Payable              10000
```

Then an external provider refund may later settle:

```text
Refund Payable
→ Provider Clearing
```

This separation allows internal accounting and provider state to differ temporarily.

---

# 54. Suspense Accounts

External financial systems frequently produce uncertain or unmatched transactions.

Example:

```text
Provider says:
payment received

Internal system:
cannot identify escrow
```

Funds must not disappear.

They may enter:

```text
Suspense
```

until operations or reconciliation identifies the correct destination.

---

# 55. Ledger Account Hotspots

A major scalability problem can occur with shared accounts such as:

```text
PLATFORM_REVENUE_NGN
```

Thousands of transactions may attempt to update the same balance row.

This creates:

```text
hot row contention
```

---

# 56. Hot Account Mitigation

Possible strategies include:

* Sharded balance buckets
* Append-only entries with asynchronous balance aggregation
* Partitioned platform accounts
* Periodic aggregation
* Avoid synchronous shared-row updates where possible

Example:

```text
PLATFORM_REVENUE_NGN_00

PLATFORM_REVENUE_NGN_01

...

PLATFORM_REVENUE_NGN_63
```

Transactions choose a bucket.

Reporting later aggregates all buckets.

---

# 57. User Account Contention

Most user accounts naturally distribute traffic.

But large marketplaces may become hot accounts.

Example:

```text
marketplace seller account
receives 50,000 releases/sec
```

This may require:

* Account sharding
* Sub-ledger accounts
* Per-escrow settlement accounts
* Batched aggregation

The data model must allow this evolution.

---

# 58. Ledger Table Growth

Assume:

```text
30,000 ledger entries/sec
```

At sustained peak:

```text
30,000 × 86,400
≈ 2.59 billion entries/day
```

Real production traffic will not sustain peak continuously, but this demonstrates why ledger storage strategy matters.

Even significantly lower averages generate billions of rows over time.

---

# 59. Partitioning

Large tables may be partitioned.

Candidates:

```text
ledger_entries

ledger_journals

payments

escrows

outbox_events

audit_events
```

Partitioning strategy must follow actual query patterns.

---

# 60. Time-Based Partitioning

Example:

```text
ledger_entries_2026_08

ledger_entries_2026_09
```

Benefits:

* Easier archival
* Smaller indexes
* Faster maintenance
* Easier retention management

Trade-off:

Queries spanning many months may touch many partitions.

---

# 61. Hash Partitioning

Example:

```text
hash(account_id)
```

Benefits:

* Spreads writes
* Helps avoid single large physical partitions

Trade-off:

* Harder time-based archival
* Cross-account queries may touch many partitions

---

# 62. Multi-Level Partitioning

Possible future approach:

```text
first:
range by month

then:
hash by account_id
```

Example:

```text
2026_08
├── bucket_00
├── bucket_01
├── ...
└── bucket_31
```

We will not implement this without benchmark evidence.

---

# 63. Partition Key Must Match Query Patterns

If our most common queries are:

```text
all entries for account_id
ordered by created_at
```

partitioning and indexes must support that.

If queries mostly use:

```text
journal_id
```

the strategy may differ.

Partitioning cannot be chosen only from row count.

---

# 64. Index Strategy

Indexes must correspond to actual access patterns.

Potential ledger indexes:

```text
business_reference UNIQUE

journal_id

account_id + created_at

account_id + id

correlation_id

created_at
```

Every index has a write cost.

Large financial tables should not accumulate indexes casually.

---

# 65. Composite Index Example

For:

```sql
SELECT *
FROM ledger_entries
WHERE account_id = ?
  AND created_at < ?
ORDER BY created_at DESC
LIMIT 100;
```

a useful index may be:

```text
(account_id, created_at DESC)
```

We will verify using:

```sql
EXPLAIN ANALYZE
```

not assumptions.

---

# 66. Avoid Deep Offset Pagination

Do not use:

```sql
OFFSET 10000000
LIMIT 100
```

for transaction history.

As offsets grow, performance degrades.

Prefer cursor/keyset pagination.

Example:

```sql
WHERE (created_at, id) < (?, ?)
ORDER BY created_at DESC, id DESC
LIMIT 100
```

---

# 67. Stable Pagination

Because multiple records may share timestamps, cursor pagination should include a unique tiebreaker.

Example cursor:

```text
createdAt
+
id
```

This produces deterministic ordering.

---

# 68. Read Replicas

Heavy historical reads may be served from PostgreSQL replicas.

Examples:

* Statements
* Historical transactions
* Operational reporting

Architecture:

```text
Primary
  |
  +→ Replica A
  +→ Replica B
```

---

# 69. Replica Lag

Replica reads may be stale.

Therefore they must not decide:

```text
Can the seller withdraw?

Has this escrow already been released?

Is this amount still refundable?
```

Financial command validation uses authoritative write-side state.

---

# 70. CQRS-Like Read Models

Some workloads benefit from separating write models from read models.

Examples:

```text
Ledger write model
→ PostgreSQL

Account summary projection
→ PostgreSQL/Redis

Transaction search
→ Elasticsearch

Analytics
→ analytical warehouse
```

We do not need full formal CQRS everywhere.

We use separate read models where workload justifies them.

---

# 71. Elasticsearch Is Not Ledger Truth

Elasticsearch may index:

```text
amount

status

buyer

seller

date
```

for investigation.

But if Elasticsearch says:

```text
available balance = 10000
```

that must not authorize a payout.

Only Ledger authoritative state can do that.

---

# 72. Redis Is Not Ledger Truth

Redis may cache:

```text
account summary
```

for fast UI reads.

Financial commands must not trust cached values without authoritative validation.

---

# 73. Connection Pooling

Each Spring Boot service will use HikariCP.

A database connection is an expensive limited resource.

We must never assume:

```text
more connections
=
more throughput
```

Too many connections can reduce PostgreSQL performance.

---

# 74. Global Connection Budget

Suppose Ledger PostgreSQL safely supports:

```text
800 service-side connections
```

and we reserve:

```text
100
```

for:

* Operations
* Replication
* Migrations
* Safety margin

Application budget:

```text
700
```

If maximum Ledger Service replicas:

```text
70
```

then pool per instance should be approximately:

```text
700 / 70 = 10
```

not:

```text
100
```

per instance.

---

# 75. Scaling Application Replicas

Suppose:

```text
20 pods × 10 connections
=
200
```

Then autoscaling reaches:

```text
70 pods
```

Total:

```text
700 connections
```

This is why HPA configuration and Hikari configuration are connected.

---

# 76. HikariCP Metrics

We must monitor:

```text
active connections

idle connections

pending threads

connection acquisition time

connection timeout count

max pool size
```

A rising pending-thread count often indicates database pressure.

---

# 77. Connection Acquisition Timeout

Requests must not wait indefinitely for a database connection.

Example:

```text
connectionTimeout
```

must be bounded.

When pool saturation occurs, the service should:

* Apply backpressure
* Reject non-critical work
* Shed load
* Surface metrics

rather than creating unlimited connections.

---

# 78. PgBouncer

At higher scale, we may introduce:

```text
PgBouncer
```

between applications and PostgreSQL.

Conceptually:

```text
1000 application-side logical connections
          ↓
       PgBouncer
          ↓
300 PostgreSQL server connections
```

It reduces server connection pressure.

---

# 79. PgBouncer Pooling Modes

We will later compare:

```text
session pooling

transaction pooling

statement pooling
```

Transaction pooling is common for large stateless applications but can conflict with some session-level PostgreSQL features.

This requires testing with:

* JPA
* prepared statements
* migrations
* advisory locks

---

# 80. Long Transactions Are Dangerous

Long transactions cause:

* Lock retention
* Deadlocks
* MVCC bloat
* Vacuum difficulty
* Connection exhaustion
* Replica lag

Financial transactions should do only necessary database work.

---

# 81. External Calls Outside Transactions

Never hold a transaction open while waiting for:

```text
bank API

payment provider

email provider

Kafka response

RabbitMQ worker
```

unless there is a very specific proven reason.

Network latency is unpredictable.

---

# 82. Database Deadlocks

Deadlocks can occur when transactions acquire locks in different orders.

Example:

```text
Transaction A:
locks account 1
then account 2

Transaction B:
locks account 2
then account 1
```

PostgreSQL will abort one transaction.

---

# 83. Lock Ordering

Where multiple account locks are required, acquire them in deterministic order.

Example:

```text
sort account IDs

lock lowest first
```

This reduces deadlock probability.

---

# 84. Deadlock Retry

A deadlock may be retried when:

* Command is idempotent
* Retry count is bounded
* Backoff/jitter is used
* No external irreversible side effect occurred

We must not blindly retry every database error.

---

# 85. Outbox Table

Every service publishing Kafka events may maintain:

```text
outbox_events
```

Conceptual columns:

```text
id

aggregate_type

aggregate_id

aggregate_version

event_type

event_version

partition_key

payload

correlation_id

causation_id

created_at

published_at

attempt_count
```

---

# 86. Outbox Indexes

Important query:

```sql
WHERE published_at IS NULL
ORDER BY created_at
LIMIT ?
```

A partial index may be useful:

```sql
CREATE INDEX ...
ON outbox_events(created_at)
WHERE published_at IS NULL;
```

This prevents scanning millions of already-published events.

---

# 87. Outbox Retention

Published outbox records should not grow forever in primary operational tables.

Possible strategies:

* Periodic archival
* Partitioning
* Delete after safe retention
* CDC-based architecture

Event history itself remains available in Kafka/object storage according to retention policy.

---

# 88. Inbox Table

Consumers may maintain:

```text
consumer_inbox
```

Columns:

```text
consumer_name

event_id

aggregate_id

processed_at
```

Constraint:

```text
UNIQUE(consumer_name, event_id)
```

Old inbox entries require a retention strategy.

Retention must be long enough to cover possible event replay/redelivery windows.

---

# 89. Database Sharding

Sharding means distributing records across independent database nodes.

Example:

```text
Shard 1:
users 0–25M

Shard 2:
users 25–50M

Shard 3:
users 50–75M

Shard 4:
users 75–100M
```

But range-based user IDs may create uneven growth.

Hash-based approaches often distribute workload better.

---

# 90. Sharding Is Not Our First Step

We should not shard immediately.

Before sharding:

* Optimize queries
* Add proper indexes
* Partition tables
* Use replicas
* Reduce unnecessary reads
* Scale database hardware
* Archive old data
* Measure contention

Sharding introduces:

* Routing complexity
* Cross-shard queries
* Rebalancing difficulty
* More operational overhead
* Distributed uniqueness issues

---

# 91. Shard Key

A shard key should have:

* High cardinality
* Even distribution
* Stable ownership
* Alignment with query patterns

Possible candidates:

```text
user_id

escrow_id

account_id

marketplace_id
```

But a large marketplace may create a hot shard if partitioned solely by marketplace ID.

---

# 92. Escrow Data Sharding

Possible future strategy:

```text
hash(escrow_id)
```

This distributes independent escrow transactions well.

Queries by user may then require:

* Secondary index service
* Search read model
* User-to-escrow mapping
* Elasticsearch

This demonstrates why write and read architecture may diverge.

---

# 93. Ledger Sharding

Ledger sharding is more difficult because financial movements can touch multiple accounts.

A future approach may assign each financial account to one shard.

Cross-shard transfers then require a distributed workflow.

We will not introduce this until a single Ledger PostgreSQL cluster has been thoroughly benchmarked.

---

# 94. Ledger Partition Ownership

An alternative architecture at very high scale is:

```text
account ownership by deterministic partition
```

Example:

```text
hash(account_id) % N
```

Each partition handles a subset of accounts.

This can reduce cross-node contention.

But cross-partition journal atomicity becomes difficult.

This is an advanced design problem we will study later.

---

# 95. Database Replication

Production PostgreSQL clusters will require replication.

Typical model:

```text
Primary
   ↓
Replica
   ↓
Replica
```

If primary fails:

```text
one replica promoted
```

Applications reconnect through the database endpoint or proxy.

---

# 96. Failover and In-Flight Requests

During failover:

* Connections may break.
* Transactions may abort.
* Clients may retry.
* Application requests may time out.

This is why financial commands require idempotency.

A timeout does not necessarily prove:

```text
operation failed
```

The caller must safely retry using the same idempotency key.

---

# 97. Commit Ambiguity

Consider:

```text
COMMIT succeeds on database

network fails before client receives confirmation
```

Application sees:

```text
connection lost
```

It may not know whether transaction committed.

This is called an ambiguous outcome.

Unique business references allow safe retry and lookup.

---

# 98. Retry After Ambiguous Commit

Client retries:

```text
ReleaseEscrowFunds
businessReference =
RELEASE:esc_123:1
```

Database detects existing journal.

Instead of another release:

```text
return existing result
```

This is exactly-once business effect built through idempotency.

---

# 99. Reconciliation

Reconciliation compares external truth with internal truth.

Examples:

```text
Provider Payment
vs
Payment DB

Payment DB
vs
Ledger

Ledger
vs
Bank settlement account

Payout DB
vs
Payout provider
```

Reconciliation is not optional in financial systems.

---

# 100. Internal Ledger Reconciliation

We should continuously verify:

```text
all journals balanced

no orphan ledger entries

no duplicate business references

materialized balances match ledger entries

held funds match escrow obligations
```

---

# 101. External Reconciliation

Example:

Provider settlement file contains:

```text
PAYMENT-999 = SUCCESS
```

Internal Payment DB says:

```text
PROCESSING
```

Reconciliation creates a case or safely updates provider state.

It must not bypass ledger idempotency.

---

# 102. Reconciliation Table

Conceptual record:

```text
reconciliation_cases
--------------------------------
id
provider
reference
case_type
expected_state
actual_state
severity
status
created_at
resolved_at
resolution
```

---

# 103. Financial Audit Trail

For any amount, we must be able to answer:

```text
Where did this money come from?

Which escrow did it belong to?

Which payment funded it?

Which journal moved it?

Which user received it?

Which payout moved it externally?

Was it ever reversed?
```

This chain should remain queryable.

---

# 104. Correlation Identifiers

Financial records should include stable references such as:

```text
escrow_id

payment_id

journal_id

payout_id

correlation_id
```

But avoid placing every possible foreign identifier in every table.

The data model should remain normalized around ownership.

---

# 105. Database Constraints

The database should enforce as many invariants as practical.

Examples:

```text
UNIQUE(business_reference)

UNIQUE(provider, provider_reference)

UNIQUE(scope, idempotency_key)

CHECK(amount_minor > 0)

CHECK(currency length = 3)

NOT NULL on required fields

FOREIGN KEY inside one service-owned database
```

---

# 106. Cross-Service Foreign Keys

Do not create database-level foreign keys from:

```text
ledger_db
```

to:

```text
escrow_db
```

Instead:

```text
escrow_id UUID
```

is stored as an external business reference.

Service boundaries remain independent.

---

# 107. Foreign Keys Inside a Service

Inside Ledger database:

```text
ledger_entries.journal_id
```

can reference:

```text
ledger_journals.id
```

because both belong to one bounded context.

Foreign keys are not forbidden.

Cross-service coupling is the problem.

---

# 108. Soft Delete

Financial tables must generally not use normal deletion.

Examples such as:

```text
ledger_entries
journals
payments
```

must remain historically available.

Domain records may use status changes rather than deletion.

---

# 109. Data Retention

Financial records may need many years of retention.

The exact period depends on jurisdiction.

Architecture must support:

* Hot recent data
* Warm historical data
* Archived records
* Legal holds

---

# 110. Archival

Old immutable data may move to cheaper storage.

Example:

```text
PostgreSQL hot partitions
→ object storage / warehouse
```

But archival must not prevent required:

* Audit investigation
* Reconciliation
* Regulatory access

---

# 111. Data Lifecycle

Example:

```text
0–6 months
→ hot PostgreSQL

6–24 months
→ warm PostgreSQL / compressed storage

older
→ archive
```

Actual policy will follow business and legal requirements.

---

# 112. Database Backup

Production design must support:

* Automated backups
* Point-in-time recovery
* WAL archiving
* Restore testing
* Encrypted backups

A backup strategy is useless unless restore procedures are tested.

---

# 113. Recovery Point Objective

For confirmed financial records:

```text
RPO ≈ zero
```

A committed acknowledged journal must not disappear during ordinary failover.

---

# 114. Recovery Time Objective

Initial critical service target:

```text
RTO < 30 minutes
```

Production maturity should reduce this further.

Exact values will be defined in disaster recovery design.

---

# 115. Database Migrations

Schema migration tool:

```text
Flyway
```

Every service owns its migrations.

Example:

```text
ledger-service
└── db/migration
```

---

# 116. Zero-Downtime Migration Principle

Avoid deployments requiring:

```text
all old pods stop

migration runs

all new pods start
```

when possible.

Prefer backward-compatible migrations.

---

# 117. Expand and Contract Migration

Example: rename a column.

Bad:

```text
rename old column
deploy new code
```

Better:

```text
1. Add new column

2. Deploy code writing both

3. Backfill

4. Deploy readers using new column

5. Stop old writes

6. Remove old column later
```

This supports rolling deployment.

---

# 118. Large Backfills

Never perform massive backfills carelessly inside a single transaction.

Example:

```text
UPDATE 1 billion rows
```

can cause:

* Locks
* WAL explosion
* Replica lag
* Table bloat

Use:

* Batches
* Rate limits
* Checkpoints
* Background migrations

---

# 119. Query Timeout

Every production service should have bounded database query time.

Long-running requests should not occupy connections indefinitely.

Different workloads may receive different timeout policies.

---

# 120. Statement Monitoring

We will monitor:

```text
slow queries

lock waits

deadlocks

rows scanned

temporary files

buffer cache hit rate

WAL generation

replication lag
```

PostgreSQL extensions/tools may include:

```text
pg_stat_statements
```

---

# 121. Explain Plans

Before optimizing queries, use:

```sql
EXPLAIN
```

and:

```sql
EXPLAIN ANALYZE
```

We should learn to interpret:

* Sequential scan
* Index scan
* Bitmap scan
* Join strategies
* Sort cost
* Estimated versus actual rows

---

# 122. ORM Strategy

JPA/Hibernate is useful for many domain operations.

But we will not force every high-volume database interaction through JPA.

Possible usage:

```text
JPA
→ ordinary aggregate persistence

JdbcTemplate / jOOQ / native SQL
→ performance-sensitive ledger operations
→ bulk operations
→ specialized queries
```

The technology should match the workload.

---

# 123. Avoid Large JPA Graphs

Do not model:

```text
Escrow
  ├── User
  ├── 10,000 Messages
  ├── Payments
  ├── LedgerEntries
  ├── AuditRecords
  └── Evidence
```

as one eagerly loaded object graph.

This creates:

* N+1 queries
* memory pressure
* huge joins
* accidental cascading
* slow transactions

Aggregates stay small.

---

# 124. Bulk Writes

High-volume pipelines may use batch inserts.

Example:

```text
audit records

projection updates

archival jobs
```

But financial journal commits should preserve clear atomicity and idempotency.

Batching should not hide correctness boundaries.

---

# 125. Data Consistency Matrix

| Data                    | Consistency                  |
| ----------------------- | ---------------------------- |
| Ledger journal          | Strong                       |
| Available balance       | Strong                       |
| Escrow state            | Strong                       |
| Payment state           | Strong within Payment domain |
| Payout reservation      | Strong                       |
| Search projection       | Eventual                     |
| Analytics               | Eventual                     |
| Notifications           | Eventual                     |
| Cached transaction view | Eventual                     |
| Dashboard totals        | Eventual                     |

---

# 126. Financial Write Priority

When competing goals conflict:

```text
correctness
>
durability
>
auditability
>
availability
>
latency
```

For example, if Ledger cannot confirm available balance:

```text
reject or delay payout
```

Do not guess from cache.

---

# 127. First Ledger Implementation Scope

The first implementation should support:

```text
Create ledger account

Post funding journal

Query account balance

Post release journal

Reject duplicate journal

Prevent insufficient balance

Maintain account balance projection

Publish ledger event through outbox
```

---

# 128. First Funding Ledger Flow

Input:

```text
SecureEscrowFunding
```

Fields:

```text
commandId

paymentId

escrowId

amountMinor

currency

correlationId
```

Validation:

```text
amount > 0

currency supported

business reference not already processed
```

Transaction:

```text
BEGIN

create journal

create clearing debit

create escrow held credit

update balance projections

insert outbox:
EscrowFundingSecured

COMMIT
```

---

# 129. First Release Ledger Flow

Input:

```text
ReleaseEscrowFunds
```

Fields:

```text
commandId

escrowId

releaseId

grossAmount

feeAmount

sellerId

currency

correlationId
```

Transaction:

```text
BEGIN

lock/check escrow held account

verify sufficient amount

create journal

debit escrow held

credit seller available

credit platform fee

update balance projections

insert outbox:
EscrowFundsReleased

COMMIT
```

---

# 130. Duplicate Release Example

First request:

```text
businessReference =
RELEASE:esc_123:1
```

Journal created successfully.

Network fails.

Retry arrives.

Database:

```text
UNIQUE(business_reference)
```

detects existing operation.

Service retrieves existing journal and returns the original business result.

No second payout occurs.

---

# 131. Required Ledger Tests

Unit tests:

```text
balanced journal accepted

unbalanced journal rejected

mixed currency journal rejected

negative amount rejected
```

Integration tests:

```text
journal commits atomically

duplicate business reference rejected

balance projection updated

outbox event committed
```

Concurrency tests:

```text
two simultaneous releases

two simultaneous payout reservations

refund versus release

two adjustments to same account
```

---

# 132. Required Database Load Tests

We will measure:

```text
journal writes/sec

entry writes/sec

lock wait time

p95 transaction latency

p99 transaction latency

connection pool saturation

deadlock rate

WAL volume

replica lag
```

---

# 133. Ledger Failure Scenarios

We must test:

```text
database unavailable before transaction

database dies during transaction

commit succeeds but response lost

Kafka unavailable after commit

outbox publisher crashes

duplicate event delivered

consumer restarts

connection pool exhausted

deadlock occurs

read replica falls behind
```

---

# 134. Important Architectural Rule

Kafka does not make financial writes safe.

RabbitMQ does not make financial writes safe.

Redis locks do not make financial writes safe.

Kubernetes does not make financial writes safe.

The authoritative financial invariant must ultimately be protected by:

```text
database transaction
+
constraints
+
locking/concurrency control
+
idempotency
+
reconciliation
```

---

# 135. Initial Database Technology Stack

```text
PostgreSQL

Flyway

HikariCP

Spring Data JPA

JdbcTemplate / native SQL where justified

Testcontainers

PgBouncer later
```

---

# 136. Future Advanced Topics

As the project matures, we will investigate:

```text
table partitioning benchmarks

ledger sharding

multi-region financial writes

logical replication

CDC with Debezium

database proxies

connection multiplexing

distributed SQL alternatives

event sourcing comparison

immutable analytical ledger copies
```

These will be introduced only after we understand the PostgreSQL architecture deeply.

---

# 137. Key Data Principles

```text
One authoritative owner per domain.

PostgreSQL owns authoritative transactional state.

Ledger entries are immutable.

Every journal balances.

Every financial business reference is unique.

No cross-service database writes.

No long transaction around network calls.

No financial decision from Redis.

No financial decision from Elasticsearch.

No duplicate financial effect after retry.

No unbounded database connection pools.

No arbitrary indexes.

No deep offset pagination at scale.

No sharding before measurement.

No financial correction through direct row editing.
```

---

# 138. Next Document

The next pre-build document should be:

```text
docs/architecture/api-design-specification.md
```

It will define:

* URL conventions
* Resource naming
* Request/response structure
* API versioning
* Authentication headers
* Idempotency keys
* Correlation IDs
* Pagination
* Cursor design
* Error responses
* Rate limiting
* Optimistic concurrency
* Async financial operations
* Webhooks
* API contracts
* OpenAPI
* Bulk APIs
* Retry semantics
