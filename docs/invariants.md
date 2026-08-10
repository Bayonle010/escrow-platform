# Escrow Platform System Invariants

**Version:** 1.0
**Status:** Draft
**System:** General-Purpose Escrow Platform

---

## 1. Purpose

This document defines the conditions that must remain true throughout the escrow platform.

These conditions are called **invariants**.

An invariant must remain true during:

* Normal request processing
* Concurrent requests
* Duplicate requests
* Service restarts
* Database failures
* Message redelivery
* Network timeouts
* Payment provider failures
* Deployment
* Event replay
* Regional outages
* Manual administrative operations

The system must not depend on users, networks, payment providers, Kafka, RabbitMQ, Redis, or Elasticsearch behaving perfectly.

Correctness must be enforced by the authoritative service and database responsible for each business operation.

---

## 2. Why Invariants Matter

A controller returning `200 OK` does not prove that a distributed financial operation completed correctly.

For example, a release request may experience the following sequence:

```text
Client sends release request
        ↓
Ledger transaction commits
        ↓
Network connection fails
        ↓
Client receives timeout
        ↓
Client retries release request
```

The system must recognise that the release has already been completed and must not release the funds a second time.

The critical question is therefore not:

```text
Did the request succeed?
```

The critical question is:

```text
What business effect has already been committed?
```

---

## 3. Invariant Categories

The platform defines invariants for:

1. Money representation
2. Ledger correctness
3. Escrow funding
4. Fund release
5. Refunds and reversals
6. Payouts
7. Transaction state
8. Idempotency
9. Concurrency
10. Events and messaging
11. Payment providers
12. Data ownership
13. Security and authorization
14. Auditability
15. Reconciliation
16. Search and caching
17. Administrative operations
18. Failure recovery

---

# 4. Money Representation Invariants

## INV-MON-001: Money must not use floating-point numbers

Financial amounts must not be represented using `float` or `double`.

For example:

```text
£15.25 = 1,525 minor units
```

A monetary value must contain:

* Amount in minor units
* Currency

Example conceptual representation:

```text
Money {
    amountMinor: 1525
    currency: GBP
}
```

Java implementations may use:

* `long` minor units
* `BigInteger` where larger ranges are required
* `BigDecimal` only with explicit scale and rounding rules

---

## INV-MON-002: Currency must always be explicit

An amount without a currency is invalid.

This is invalid:

```text
amount = 10000
```

This is valid:

```text
amountMinor = 10000
currency = GBP
```

---

## INV-MON-003: Different currencies must not be added directly

The system must not perform:

```text
£100 + €100
```

Currency conversion must be an explicit business operation with:

* Source currency
* Destination currency
* Exchange rate
* Rate provider
* Rate timestamp
* Rounding rule
* Conversion fee
* Conversion reference

---

## INV-MON-004: Monetary rounding rules must be deterministic

Every operation that can create fractional minor units must use a defined rounding rule.

The same input must always produce the same monetary result.

---

## INV-MON-005: Negative amounts require explicit business meaning

Ordinary payment, funding, release, and payout commands must not accept negative amounts.

Corrections must be represented as:

* Reversals
* Counter-entries
* Refunds
* Adjustments

They must not silently modify the original amount.

---

# 5. Ledger Invariants

## INV-LED-001: Every journal must balance

For every committed journal:

```text
Total debits = Total credits
```

Example:

```text
Debit:  Escrow held account       £100
Credit: Seller available account   £98
Credit: Platform fee account        £2
```

Therefore:

```text
£100 debit = £100 credit
```

An unbalanced journal must be rejected before it is committed.

---

## INV-LED-002: Ledger entries are immutable

A committed ledger entry must never be updated or deleted.

Corrections must be represented by new entries that reverse or adjust the earlier entries.

---

## INV-LED-003: Every ledger entry belongs to one journal

A ledger entry must reference a valid journal.

A journal groups all debits and credits belonging to one atomic financial operation.

---

## INV-LED-004: A journal must be committed atomically

All entries belonging to one journal must either:

* Commit together
* Roll back together

The system must never persist only some entries from a journal.

---

## INV-LED-005: Every financial journal must have a unique business reference

Examples include:

```text
FUNDING:{paymentId}
RELEASE:{escrowId}:{releaseNumber}
REFUND:{refundId}
PAYOUT:{payoutId}
REVERSAL:{originalJournalId}
```

The same business reference must not create multiple journals.

---

## INV-LED-006: Ledger accounts must have a defined type

Examples include:

* Asset
* Liability
* Revenue
* Expense
* Clearing
* Suspense

The account type determines its normal debit or credit behaviour.

---

## INV-LED-007: Ledger account currency is fixed

A ledger account must not contain balances from different currencies.

A GBP account must contain only GBP-denominated entries.

---

## INV-LED-008: Financial balances must be derived from ledger entries

The ledger is the source of truth for financial balances.

A cached balance or materialised balance may be used for performance, but it must be reconcilable with the immutable ledger.

---

## INV-LED-009: Available balance must not exceed ledger balance

Funds may exist in the ledger while being unavailable because they are:

* Reserved
* Pending
* Frozen
* Disputed
* Awaiting settlement

The available amount must not exceed the amount legally available for use.

---

## INV-LED-010: The system must prevent overspending

Two concurrent commands must not successfully spend the same available funds.

This invariant must be protected using a correct concurrency strategy such as:

* Row-level locking
* Optimistic versioning
* Atomic conditional updates
* Serialised account processing
* Partitioned ledger ownership

A distributed lock alone is not sufficient protection for authoritative financial state.

---

## INV-LED-011: No acknowledged financial journal may be silently lost

Once the platform confirms that a financial operation has committed, that journal must survive:

* Service restart
* Broker outage
* Cache failure
* Search failure
* Notification failure

---

## INV-LED-012: Ledger imbalance count must remain zero

The platform must continuously monitor for unbalanced journals.

Expected count:

```text
0
```

Any non-zero value is a critical incident.

---

# 6. Escrow Funding Invariants

## INV-FUN-001: Only an accepted escrow may be funded

Both parties must have accepted the same version of the escrow terms before funding is confirmed.

---

## INV-FUN-002: A funding confirmation must reference a valid escrow

The payment amount, currency, buyer, and escrow must match the expected funding instruction.

---

## INV-FUN-003: Client-side success is not funding confirmation

The following are not sufficient proof of payment:

* Browser redirect
* Mobile application success screen
* User-uploaded receipt
* Client-generated callback
* Unverified webhook

Funding must be verified through a trusted provider channel or reconciliation process.

---

## INV-FUN-004: One provider transaction may fund an escrow only once

Duplicate provider webhooks, API responses, retries, or reconciliation records must not produce duplicate escrow funding.

---

## INV-FUN-005: Confirmed funding must create balanced ledger entries

The escrow cannot become financially funded unless the corresponding ledger journal commits.

The transaction state and financial effect must not disagree.

---

## INV-FUN-006: Funded amount must not exceed the expected funding amount without an explicit overpayment policy

Unexpected excess payment must be:

* Rejected
* Placed in suspense
* Returned
* Handled through a defined overpayment process

It must not silently increase the escrow amount.

---

## INV-FUN-007: Partial funding requires explicit product support

An escrow is not considered fully funded unless the required amount has been secured.

Partial funding must not be accidentally treated as complete funding.

---

## INV-FUN-008: Funding currency must match escrow currency

A different currency requires an explicit conversion workflow.

---

# 7. Fund Release Invariants

## INV-REL-001: An escrow cannot release more than its releasable balance

The releasable amount is calculated from:

```text
Confirmed funding
- previous releases
- refunds
- active reservations
- applicable adjustments
```

---

## INV-REL-002: An escrow release must happen at most once per release instruction

Repeated requests using the same idempotency key or business reference must return the original result.

They must not create an additional financial effect.

---

## INV-REL-003: An unfunded escrow cannot release funds

A lifecycle state alone is not sufficient.

The platform must verify the authoritative financial balance.

---

## INV-REL-004: Disputed funds cannot be automatically released

When a dispute is active:

* Automatic release must be cancelled.
* Scheduled release jobs must recheck the current state.
* Retried release commands must be rejected or placed on hold.

---

## INV-REL-005: Frozen funds cannot be released

Funds may be frozen because of:

* Compliance review
* Fraud investigation
* Court or regulatory order
* Account restriction
* Operational incident

---

## INV-REL-006: Release fees must be deterministic

For the same accepted terms and amount, the system must calculate the same fee unless a documented versioned rule applies.

---

## INV-REL-007: Release must create one balanced financial journal

The release journal may credit:

* Seller available balance
* Platform fee revenue
* Tax liability
* Marketplace commission

The total credits must equal the escrow debit.

---

## INV-REL-008: Release state must reflect ledger truth

The escrow must not be marked `RELEASED` if its release journal failed.

The platform may use an intermediate state such as:

```text
RELEASE_PENDING
```

until the financial effect is committed.

---

## INV-REL-009: Notification failure must not reverse a successful release

Email, SMS, push, WebSocket, RabbitMQ, or notification-service failure must not undo a committed ledger operation.

---

# 8. Refund and Reversal Invariants

## INV-REF-001: Refunds must not exceed the refundable balance

The refundable amount is:

```text
Confirmed funding
- completed releases
- previous refunds
- non-refundable charges
```

---

## INV-REF-002: A refund instruction must be idempotent

Repeated refund requests must not produce repeated refunds.

---

## INV-REF-003: Refunds must produce balanced ledger entries

The financial movement must be represented in the ledger before the refund is considered financially complete.

---

## INV-REF-004: Provider refund status and ledger refund status must be distinguishable

A provider refund may be:

* Requested
* Processing
* Confirmed
* Failed
* Unknown

The system must not mark a refund complete solely because a provider request was sent.

---

## INV-REF-005: A completed release cannot be deleted

When a release must be undone, the system creates:

* A reversal
* A recovery transaction
* A negative adjustment
* A separate refund workflow

It must not remove the original ledger history.

---

## INV-REF-006: Reversals must reference the original journal

A reversal must preserve the relationship between:

* Original operation
* Reversal reason
* Reversal journal
* Authorising actor
* Time of reversal

---

## INV-REF-007: A journal cannot be reversed more than its remaining reversible amount

Partial reversals must be tracked.

---

# 9. Payout Invariants

## INV-PAY-001: A payout cannot exceed the seller’s available balance

Pending or disputed funds must not be paid out.

---

## INV-PAY-002: A payout destination must belong to or be authorised by the seller

Changing a payout destination may require:

* Reauthentication
* Multi-factor authentication
* Cooling-off period
* Fraud review

---

## INV-PAY-003: A payout request must reserve funds before external processing

This prevents the same balance from being used for multiple concurrent payouts.

---

## INV-PAY-004: A provider timeout must not immediately be treated as failure

The provider may have completed the payout even when the platform did not receive the response.

The payout may enter:

```text
UNKNOWN
```

or:

```text
RECONCILIATION_REQUIRED
```

---

## INV-PAY-005: One payout instruction must not produce multiple provider payouts

Retries must reuse the same provider idempotency reference where supported.

---

## INV-PAY-006: Failed payouts must release or preserve reservations according to confirmed provider state

Funds must not be returned to the seller’s available balance while the external outcome is unknown.

---

# 10. Transaction State Invariants

## INV-STA-001: Every escrow has one authoritative lifecycle state

Caches, Elasticsearch, analytics systems, and frontend applications may contain projections, but they are not authoritative.

---

## INV-STA-002: State transitions must be explicit

The platform must define allowed transitions.

Example:

```text
DRAFT
→ AWAITING_COUNTERPARTY
→ TERMS_ACCEPTED
→ AWAITING_FUNDING
→ FUNDING_PROCESSING
→ FUNDED
→ IN_PROGRESS
→ DELIVERED
→ INSPECTION
→ RELEASE_PENDING
→ RELEASED
```

---

## INV-STA-003: Invalid state transitions must be rejected

Examples:

```text
DRAFT → RELEASED
FUNDED → DRAFT
DISPUTED → AUTOMATICALLY_RELEASED
REFUNDED → FUNDING_PROCESSING
```

---

## INV-STA-004: Terminal states require explicit reopening rules

Terminal states include:

* Released
* Refunded
* Cancelled
* Expired

A terminal transaction must not become active again unless a documented administrative or recovery workflow exists.

---

## INV-STA-005: Every transition must record its cause

The system must record:

* Previous state
* New state
* Actor
* Command
* Timestamp
* Correlation ID
* Reason
* Version

---

## INV-STA-006: Terms acceptance applies to one terms version

When material transaction terms change:

* The terms version must increase.
* Earlier acceptance becomes invalid.
* Both parties must accept the new version.

---

## INV-STA-007: Scheduled jobs must revalidate state

A job scheduled when an escrow was in `INSPECTION` must not release it later without checking whether it has since become:

* Disputed
* Frozen
* Refunded
* Cancelled
* Released

---

# 11. Idempotency Invariants

## INV-IDM-001: Every externally accessible financial command must support idempotency

This includes:

* Funding initiation
* Funding confirmation
* Fund release
* Refund
* Reversal
* Payout
* Financial adjustment

---

## INV-IDM-002: An idempotency key is scoped

The key must be associated with:

* Calling account or integration
* Operation type
* Endpoint or command
* Request payload fingerprint

---

## INV-IDM-003: Reusing a key with a different request must be rejected

Example:

```text
Key: release-123
First request: release £100
Second request: release £200
```

The second request must not be treated as a retry of the first.

---

## INV-IDM-004: An idempotency result must survive process restart

Critical idempotency state must not exist only in application memory.

---

## INV-IDM-005: Idempotency must protect the business effect

Saving an HTTP response alone is not enough.

The authoritative operation must have a unique business constraint that prevents duplicate execution.

---

## INV-IDM-006: Consumer idempotency must be independent of broker delivery guarantees

Kafka or RabbitMQ consumers must assume that a message can be delivered more than once.

---

# 12. Concurrency Invariants

## INV-CON-001: Concurrent commands must not violate balance constraints

Examples:

* Two release requests
* Two refund requests
* Release and dispute commands
* Two payout requests
* Refund and payout commands

---

## INV-CON-002: An escrow update must detect stale versions

Optimistic versioning may be used to detect that another command changed the escrow after it was read.

---

## INV-CON-003: Lock scope must be limited

A command must not lock unrelated escrows or ledger accounts unnecessarily.

Large lock scope reduces throughput and increases deadlock risk.

---

## INV-CON-004: Deadlocks must be retryable only when safe

Deadlock retries must preserve:

* Idempotency
* Bounded retry count
* Backoff
* Original command identity

---

## INV-CON-005: Database isolation must match the invariant

The team must choose isolation and locking based on the business risk.

The default isolation level must not be assumed to protect every financial invariant automatically.

---

# 13. Event and Messaging Invariants

## INV-EVT-001: A committed business operation must not lose its required event

When a database change and an event belong to the same logical operation, the platform must use a reliable mechanism such as the transactional outbox pattern.

---

## INV-EVT-002: Events represent facts that already occurred

Examples:

```text
FundingConfirmed
EscrowReleased
DisputeOpened
```

An event name should not disguise an uncompleted command.

---

## INV-EVT-003: Events are immutable

Published events must not be edited.

A correction is represented by another event.

---

## INV-EVT-004: Every event has a globally unique event ID

Consumers use this ID for:

* Deduplication
* Audit
* Tracing
* Replay analysis

---

## INV-EVT-005: Events must be versioned

Schema changes must not silently break existing consumers.

---

## INV-EVT-006: Consumers must tolerate duplicate events

Receiving the same event twice must not create duplicate business effects.

---

## INV-EVT-007: Consumers must handle out-of-order events where ordering is not guaranteed

A consumer may:

* Ignore a stale version
* Delay processing
* Fetch authoritative state
* Store the event for retry
* Rebuild its projection

---

## INV-EVT-008: Kafka ordering is limited to a partition

Events requiring per-escrow order should use:

```text
partition key = escrowId
```

The system must not assume global ordering across all escrows.

---

## INV-EVT-009: RabbitMQ redelivery must not duplicate work effects

A worker may complete a task and crash before acknowledging the message.

The redelivered task must be safe.

---

## INV-EVT-010: Poison messages must not block an entire consumer indefinitely

Messages that repeatedly fail must eventually move to a dead-letter destination for investigation.

---

## INV-EVT-011: Retry loops must be bounded

The system must not retry forever.

Retries require:

* Maximum attempt count
* Backoff
* Jitter
* Dead-letter handling
* Monitoring

---

## INV-EVT-012: Event publication failure must be observable

Outbox backlog, producer errors, consumer lag, and dead-letter volume must generate metrics and alerts.

---

# 14. Payment Provider Invariants

## INV-PRV-001: Provider references must be unique within a provider

The same provider transaction must not map to multiple internal financial effects.

---

## INV-PRV-002: Provider webhooks must be authenticated

Webhook verification may include:

* Signature
* Timestamp
* Secret
* Certificate
* Source validation
* Replay protection

---

## INV-PRV-003: Webhook order must not be trusted

The platform may receive:

```text
payment_success
```

before:

```text
payment_processing
```

State updates must account for late or stale provider events.

---

## INV-PRV-004: Provider success must be verified against expected details

The platform must compare:

* Amount
* Currency
* Customer
* Internal reference
* Provider reference
* Payment status

---

## INV-PRV-005: Missing webhook does not prove failure

Reconciliation or provider-status polling must recover missed notifications.

---

## INV-PRV-006: Duplicate webhook delivery must be harmless

The provider event ID or transaction reference must be deduplicated.

---

## INV-PRV-007: Provider-specific behaviour must not leak into the entire domain

Provider differences should be handled behind a payment-provider abstraction or anti-corruption layer.

---

# 15. Data Ownership Invariants

## INV-DAT-001: Each domain record has one authoritative owner

Examples:

* Escrow service owns escrow agreements.
* Ledger service owns ledger journals and entries.
* Payment service owns provider payment attempts.
* Payout service owns payouts.
* Dispute service owns dispute cases.

---

## INV-DAT-002: One service must not directly update another service’s database

Cross-domain changes must occur through:

* API commands
* Events
* Explicit integration workflows

---

## INV-DAT-003: Derived copies are not authoritative

Redis, Elasticsearch, analytics stores, and local projections may contain copies.

They must not independently decide authoritative financial truth.

---

## INV-DAT-004: Sensitive data must be minimised across events

Events must not copy unnecessary:

* Identity documents
* Authentication secrets
* Card data
* Bank data
* Personal information

---

## INV-DAT-005: Data changes must be backward-compatible during rolling deployment

Database migrations and event-schema changes must allow old and new application versions to run together during deployment.

---

# 16. Security and Authorization Invariants

## INV-SEC-001: Authentication does not automatically grant resource access

Every operation must verify that the authenticated principal is authorised to act on the specific escrow, account, dispute, or payout.

---

## INV-SEC-002: A buyer cannot perform seller-only actions

Examples include:

* Marking seller fulfilment complete
* Changing the seller’s payout account
* Submitting seller-only evidence

---

## INV-SEC-003: A seller cannot approve their own delivery as the buyer

Role checks must be tied to transaction participation, not merely global account roles.

---

## INV-SEC-004: Privileged administrative operations require explicit authorization

Examples include:

* Freezing funds
* Resolving disputes
* Creating adjustments
* Restricting accounts
* Releasing held transactions

---

## INV-SEC-005: High-risk administrative financial actions require maker-checker approval

One person should not be able to create and approve certain financial corrections.

---

## INV-SEC-006: Secrets must not appear in logs or events

This includes:

* Passwords
* Tokens
* API secrets
* Private keys
* Full payment credentials
* Sensitive identity data

---

## INV-SEC-007: A revoked credential must not continue authorising new operations

Revocation must propagate within the defined security tolerance.

---

# 17. Audit Invariants

## INV-AUD-001: Every financial operation is auditable

The platform must record:

* Actor
* Operation
* Time
* Amount
* Currency
* Business reference
* Correlation ID
* Result
* Reason
* Related journal

---

## INV-AUD-002: Administrative actions are auditable

The audit trail must include:

* Before state
* After state
* Administrator identity
* Approval identity where applicable
* Reason
* Timestamp
* Request origin

---

## INV-AUD-003: Audit records are immutable

Corrections must append new audit records rather than modifying previous history.

---

## INV-AUD-004: Audit failure must not be silently ignored

When a legally or financially required audit record cannot be preserved, the operation must fail safely or enter a recoverable state.

---

## INV-AUD-005: Correlation IDs must cross service boundaries

HTTP requests, Kafka events, RabbitMQ tasks, scheduled jobs, and provider callbacks should remain traceable as one business workflow.

---

# 18. Reconciliation Invariants

## INV-REC-001: Internal records must be reconciled with payment providers

The system must compare:

* Expected payments
* Provider payments
* Expected refunds
* Provider refunds
* Expected payouts
* Provider payouts
* Fees
* Settlement amounts

---

## INV-REC-002: Unknown outcomes must remain visible

An uncertain provider operation must not be silently classified as successful or failed.

---

## INV-REC-003: Reconciliation corrections must use controlled financial entries

A reconciliation difference must not be fixed using direct balance updates.

---

## INV-REC-004: Reconciliation must itself be idempotent

Running the same reconciliation period repeatedly must not create duplicate corrections.

---

## INV-REC-005: Reconciliation differences must create operational cases

Differences require:

* Case reference
* Severity
* Assigned owner
* Evidence
* Resolution
* Audit trail

---

# 19. Redis and Cache Invariants

## INV-CAC-001: Redis is not the financial source of truth

Redis failure must not destroy:

* Balances
* Ledger entries
* Escrow funding
* Refund history
* Payout history

---

## INV-CAC-002: Cache entries must have defined ownership and expiry rules

Every cache category must define:

* Key format
* Value format
* TTL
* Invalidating events
* Maximum staleness
* Failure behaviour

---

## INV-CAC-003: Stale cache data must not authorise irreversible financial operations

Financial commands must revalidate authoritative state.

---

## INV-CAC-004: Cache miss storms must be controlled

Possible protections include:

* Request coalescing
* Short locks
* Stale-while-revalidate
* Jittered expiration
* Rate limiting

---

## INV-CAC-005: Distributed locks require expiry and ownership validation

A process must not release a lock owned by another process.

Distributed locks must not replace database constraints for financial correctness.

---

# 20. Elasticsearch Invariants

## INV-SRC-001: Elasticsearch is a derived search model

Search results may be temporarily behind the authoritative database.

---

## INV-SRC-002: Search indexing must be replayable

The platform must be able to rebuild search indexes from:

* Kafka events
* Authoritative databases
* Stored snapshots

---

## INV-SRC-003: Duplicate indexing events must be harmless

Documents should be updated using stable identifiers and versions.

---

## INV-SRC-004: Search results must not independently authorise financial decisions

A transaction appearing as funded in Elasticsearch does not prove that it is currently releasable.

---

## INV-SRC-005: Search projection lag must be measured

The platform must expose how far Elasticsearch is behind authoritative events.

---

# 21. Administrative Operation Invariants

## INV-ADM-001: Administrators cannot directly edit balances

Financial corrections must go through ledger adjustment workflows.

---

## INV-ADM-002: Every manual override requires a reason

The reason must be stored in the audit record.

---

## INV-ADM-003: High-risk manual actions may require two-person approval

Examples include:

* Large adjustments
* Forced releases
* Forced refunds
* Account unfreezing
* Dispute overrides

---

## INV-ADM-004: Manual actions must respect currency and amount limits

Administrative access does not bypass financial validation.

---

## INV-ADM-005: Administrative tools must use the same authoritative APIs

The administration interface must not perform uncontrolled direct database updates.

---

# 22. Failure and Availability Invariants

## INV-AVL-001: Notification failure must not invalidate a completed financial operation

A user may receive a delayed notification while the underlying transaction remains correct.

---

## INV-AVL-002: Search failure must not prevent authoritative transaction retrieval

The platform may degrade to direct lookup by known transaction reference.

---

## INV-AVL-003: Redis failure must degrade performance, not financial correctness

Requests may become slower or rate-limited.

They must not produce incorrect balances.

---

## INV-AVL-004: Kafka failure must not lose committed domain events

The transactional outbox must retain unpublished events for later delivery.

---

## INV-AVL-005: RabbitMQ failure must not erase required work

Tasks must remain recoverable from an authoritative record or durable queue.

---

## INV-AVL-006: Service restart must not lose in-progress financial state

Important state must not exist only in process memory.

---

## INV-AVL-007: Graceful shutdown must stop accepting new work before terminating active work

Consumers and HTTP services must support controlled shutdown.

---

## INV-AVL-008: Backpressure must be preferred over uncontrolled overload

The platform may:

* Reject requests
* Return `429 Too Many Requests`
* Return `503 Service Unavailable`
* Pause consumers
* Delay non-critical work

It must not allow unlimited queues or connections to exhaust the system.

---

# 23. Enforcement Mechanisms

Invariants may be enforced at multiple layers.

## Application layer

Examples:

* Command validation
* Authorization checks
* State-machine rules
* Fee calculations
* Currency checks

## Database layer

Examples:

* Unique constraints
* Foreign keys
* Check constraints
* Version columns
* Atomic transactions
* Row locks
* Conditional updates

## Messaging layer

Examples:

* Event IDs
* Consumer inbox tables
* Partition keys
* Dead-letter queues
* Retry policies

## Operational layer

Examples:

* Reconciliation
* Monitoring
* Alerts
* Audit review
* Incident response

Critical invariants should not depend on only one defensive layer.

---

# 24. Required Database Constraints

The design should eventually include constraints similar to:

```text
Unique provider transaction reference
Unique journal business reference
Unique event ID
Unique idempotency scope and key
Unique consumer and event ID
Positive monetary amount where required
Valid currency code
Balanced journal validation
Escrow version for optimistic locking
```

Some invariants cannot be expressed using a simple SQL constraint and will require transactional application logic.

---

# 25. Required Concurrency Tests

The project must include tests for:

1. Two simultaneous funding confirmations
2. Two simultaneous release requests
3. Release and dispute submitted concurrently
4. Two simultaneous refunds
5. Refund and release submitted concurrently
6. Two simultaneous payouts from one balance
7. Duplicate Kafka events
8. Duplicate RabbitMQ jobs
9. Consumer crash after database commit but before acknowledgement
10. Provider webhook delivered before API response
11. Provider success followed by request timeout
12. Stale escrow version update
13. Database deadlock retry
14. Redis outage during a transaction
15. Elasticsearch outage during indexing
16. Kafka outage after business transaction commit

---

# 26. Required Property-Based Tests

Where appropriate, generated test data should verify properties such as:

```text
For every committed journal:
sum(debits) = sum(credits)
```

```text
For every escrow:
released + refunded <= confirmed funding
```

```text
For every payout:
payout amount <= reserved seller balance
```

```text
For every duplicate command:
business effect count <= 1
```

```text
For every state transition:
transition belongs to allowed transition set
```

---

# 27. Required Monitoring Alerts

Critical alerts include:

* Ledger imbalance detected
* Duplicate journal business reference attempt
* Negative available balance
* Refund exceeds refundable amount
* Release exceeds funded amount
* Unexpected state transition
* Outbox publication lag
* Kafka consumer lag
* RabbitMQ dead-letter growth
* Reconciliation difference
* Payment outcome unknown for too long
* Payout outcome unknown for too long
* Database connection pool saturation
* Unusual administrative adjustment
* Search projection lag
* Redis memory pressure

---

# 28. Invariant Priority

When system goals conflict, use this priority order:

```text
1. Financial correctness
2. Security and legal compliance
3. Data durability
4. Auditability
5. Availability
6. Latency
7. Convenience
```

For example, the platform should reject or delay a release when it cannot safely confirm the available balance.

It must not release funds merely to maintain a low response time.

---

# 29. Definition of a Correct Financial Operation

A financial operation is complete only when:

1. The command has been authorised.
2. The business state has been validated.
3. The idempotency identity has been established.
4. The financial journal has committed.
5. Required domain state has committed.
6. The required outbox event has committed.
7. The operation can be reconstructed from audit records.
8. A retry cannot duplicate the financial effect.

Notifications, search indexing, analytics, and external projections may complete asynchronously.

---

# 30. Next Document

The next document is:

```text
docs/state-machine.md
```

It will define:

* Every escrow state
* Every allowed transition
* The command that causes each transition
* The actor permitted to issue the command
* Preconditions
* Financial effects
* Events produced
* Invalid transitions
* Timeout behaviour
* Dispute behaviour
* Terminal states
