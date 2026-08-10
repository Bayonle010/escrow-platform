# Escrow Platform Domain Model and Bounded Contexts

**Version:** 1.0
**Status:** Draft
**System:** General-Purpose Escrow Platform

---

## 1. Purpose

This document defines the major business domains in the escrow platform.

The goal is to prevent a common microservice mistake:

```text
One database table
=
One microservice
```

A microservice should normally represent a meaningful business capability with clear ownership, rules, and data.

This document identifies:

* Bounded contexts
* Aggregates
* Entities
* Value objects
* Ownership boundaries
* Transaction boundaries
* Cross-domain communication
* Candidate future microservices

---

# 2. Core Business Domains

The platform contains the following major domains:

```text
Identity & Access
Escrow
Payments
Ledger
Payouts
Disputes
Risk & Compliance
Notifications
Messaging
Search
Audit
Reconciliation
```

These domains do not all have equal importance.

The core financial domains are:

```text
Escrow
Payments
Ledger
Payouts
Disputes
```

Supporting domains include:

```text
Identity
Notifications
Messaging
Search
Audit
Risk
Reconciliation
```

---

# 3. Bounded Context: Identity and Access

## Responsibilities

The Identity domain manages:

* User registration
* Authentication
* Passwords
* Multi-factor authentication
* Sessions
* Roles
* Permissions
* Device information
* Account status
* API credentials
* Business identities

It answers:

```text
Who is this user?
```

and:

```text
What is this user allowed to do?
```

## Aggregate

```text
User
```

Possible structure:

```text
User
├── userId
├── email
├── phone
├── passwordCredential
├── status
├── verificationLevel
├── roles
├── createdAt
└── version
```

## Value objects

Examples:

```text
EmailAddress
PhoneNumber
UserId
Role
AccountStatus
```

## Important rule

Identity owns authentication.

It does not decide whether a user may release a specific escrow.

That authorization belongs partly to the Escrow domain.

Example:

```text
Identity:
User is authenticated.

Escrow:
User is the buyer for escrow 123.

Therefore:
User may AcceptDelivery.
```

---

# 4. Bounded Context: Escrow

This is the central business domain.

## Responsibilities

The Escrow domain owns:

* Escrow creation
* Buyer and seller association
* Terms
* Terms versions
* Terms acceptance
* Transaction lifecycle
* Delivery state
* Inspection period
* Release eligibility
* Refund eligibility
* Escrow state transitions

It answers:

```text
What has been agreed?
```

and:

```text
What is allowed to happen next?
```

## Aggregate Root

```text
Escrow
```

Example:

```text
Escrow
├── escrowId
├── buyerId
├── sellerId
├── currentTermsVersion
├── state
├── amount
├── currency
├── inspectionPeriod
├── deliveryDeadline
├── createdAt
├── updatedAt
└── version
```

The aggregate protects rules such as:

```text
Buyer != Seller

Amount > 0

Cannot fund before terms acceptance

Cannot release while disputed

Cannot accept delivery before delivery

Cannot reopen terminal escrow without explicit workflow
```

---

# 5. Escrow Terms

Escrow terms should not be stored merely as mutable columns on the escrow record.

Terms are versioned.

Example:

```text
EscrowTerms
├── escrowId
├── version
├── amount
├── currency
├── description
├── category
├── deliveryDeadline
├── inspectionPeriod
├── releaseRules
├── refundRules
├── createdAt
└── createdBy
```

Acceptance references a specific version:

```text
TermsAcceptance
├── escrowId
├── termsVersion
├── participantId
├── acceptedAt
└── acceptanceReference
```

If terms change:

```text
version 3
→ version 4
```

Earlier acceptance of version 3 does not mean acceptance of version 4.

---

# 6. Money Value Object

Money must be modelled explicitly.

Conceptually:

```text
Money
├── amountMinor
└── currency
```

Example:

```text
10,500 minor units
NGN
```

means:

```text
₦105.00
```

depending on the currency's minor-unit definition.

The Money object must enforce:

* Currency
* Valid amount
* Safe arithmetic
* Comparison rules
* Currency matching
* Rounding rules

---

# 7. Bounded Context: Payments

The Payment domain is responsible for money entering the platform.

It answers:

```text
Did the buyer's payment actually succeed?
```

## Responsibilities

* Create funding instructions
* Integrate with payment providers
* Track payment attempts
* Handle provider callbacks
* Verify payment status
* Detect duplicate provider events
* Manage unknown outcomes
* Reconcile provider payments

## Aggregate Root

```text
Payment
```

Example:

```text
Payment
├── paymentId
├── escrowId
├── payerId
├── amount
├── currency
├── provider
├── providerReference
├── status
├── idempotencyKey
├── createdAt
└── version
```

Possible statuses:

```text
CREATED
PENDING
PROCESSING
SUCCEEDED
FAILED
UNKNOWN
CANCELLED
```

---

# 8. Payment Does Not Own Escrow State

Payment may determine:

```text
Payment = SUCCEEDED
```

but it must not directly modify the escrow database.

Bad:

```text
Payment Service
    ↓
UPDATE escrow SET state = 'FUNDED'
```

Better:

```text
Payment
    ↓
FundingConfirmed
    ↓
Escrow
    ↓
validates current state
    ↓
FUNDED
```

The Escrow domain owns the escrow lifecycle.

---

# 9. Bounded Context: Ledger

The Ledger domain is the authoritative financial accounting system.

It answers:

```text
Where is the money?
```

This is different from:

```text
What state is the escrow in?
```

## Responsibilities

* Accounts
* Journals
* Ledger entries
* Holds
* Reservations
* Balance projections
* Transfers
* Reversals
* Adjustments
* Financial audit trail

## Aggregate Concepts

A simple model may include:

```text
LedgerAccount

Journal

LedgerEntry
```

Example account types:

```text
Buyer Funding Clearing
Escrow Held Funds
Seller Available Balance
Platform Fee Revenue
Refund Liability
Payout Clearing
Suspense
```

---

# 10. Journal

A Journal represents one atomic financial operation.

Example:

```text
Journal
├── journalId
├── businessReference
├── journalType
├── currency
├── status
├── createdAt
└── entries
```

Example entries:

```text
Debit Escrow Held          10000
Credit Seller Available     9800
Credit Platform Revenue      200
```

The journal must balance before commit.

---

# 11. Ledger Account

Example:

```text
LedgerAccount
├── accountId
├── ownerType
├── ownerId
├── accountType
├── currency
├── status
└── version
```

Examples:

```text
SELLER_AVAILABLE
ESCROW_HELD
PLATFORM_REVENUE
PROVIDER_CLEARING
PAYOUT_PENDING
REFUND_PENDING
SUSPENSE
```

An account should represent one currency.

---

# 12. Ledger Ownership

No other domain may directly update ledger entries.

Bad:

```text
Escrow Service
    ↓
INSERT INTO ledger_entries
```

Better:

```text
Escrow
    ↓ ReleaseFunds command
Ledger
    ↓ commits balanced journal
    ↓ FundsReleased
Escrow
    ↓ moves to RELEASED
```

---

# 13. Bounded Context: Payout

Payment and Payout are deliberately separated.

Payment:

```text
External money
→ Platform
```

Payout:

```text
Platform
→ External seller account
```

## Responsibilities

* Payout methods
* Bank accounts
* Provider payout requests
* Payout reservations
* Payout retries
* Payout reconciliation
* Provider status
* Unknown payout outcomes

## Aggregate Root

```text
Payout
```

Example:

```text
Payout
├── payoutId
├── sellerId
├── amount
├── currency
├── destinationId
├── provider
├── providerReference
├── status
├── createdAt
└── version
```

Possible states:

```text
REQUESTED
RESERVED
PROCESSING
SUCCEEDED
FAILED
UNKNOWN
CANCELLED
```

---

# 14. Bounded Context: Dispute

A dispute is complex enough to become its own domain.

It answers:

```text
The buyer and seller disagree. What happens now?
```

## Responsibilities

* Dispute creation
* Dispute reasons
* Evidence
* Responses
* Timelines
* Officer assignment
* Resolution
* Appeals
* Split decisions
* Escalation

## Aggregate Root

```text
Dispute
```

Example:

```text
Dispute
├── disputeId
├── escrowId
├── openedBy
├── reason
├── status
├── requestedOutcome
├── assignedOfficer
├── openedAt
├── resolvedAt
└── version
```

Possible states:

```text
OPEN
AWAITING_BUYER
AWAITING_SELLER
UNDER_REVIEW
ESCALATED
RESOLVED
CLOSED
```

---

# 15. Dispute and Escrow Relationship

Opening a dispute should affect the escrow lifecycle.

Example:

```text
Buyer
  ↓ OpenDispute
Dispute Domain
  ↓ DisputeOpened
Escrow Domain
  ↓
DISPUTED
```

Resolution may produce:

```text
DisputeResolvedForBuyer
```

or:

```text
DisputeResolvedForSeller
```

or:

```text
DisputeResolvedSplit
```

The Escrow domain then determines the appropriate financial workflow.

---

# 16. Bounded Context: Risk and Compliance

Risk and Compliance determine whether operations should be:

```text
ALLOWED
REVIEW_REQUIRED
HELD
REJECTED
```

## Responsibilities

* Transaction monitoring
* Risk scoring
* Velocity checks
* User restrictions
* Compliance holds
* KYC/KYB status
* Sanctions checks
* Fraud signals
* Manual review cases

This domain may consume high volumes of Kafka events.

Example:

```text
PaymentSucceeded
EscrowCreated
PayoutRequested
LoginDetected
DisputeOpened
```

and continuously evaluate risk.

---

# 17. Bounded Context: Messaging

The Messaging domain owns communication between buyer and seller.

## Responsibilities

* Conversation creation
* Messages
* Attachments
* Read status
* Delivery status
* Moderation
* System messages
* Real-time delivery

Potential aggregate:

```text
Conversation
├── conversationId
├── escrowId
├── participants
└── status
```

Messages may be high-volume and eventually require their own storage and scaling strategy.

---

# 18. Bounded Context: Notification

Notifications are not part of financial correctness.

The Notification domain owns:

* Email
* SMS
* Push
* In-app notifications
* Preferences
* Templates
* Retry policies

Example:

```text
EscrowFunded
    ↓
Notification Consumer
    ↓
RabbitMQ
    ↓
Send Email Worker
```

A failed notification must not roll back the escrow funding.

---

# 19. Bounded Context: Search

Search is a read-oriented domain.

It owns derived documents for:

* Escrows
* Users
* Disputes
* Payments
* Administrative investigation

Primary technology:

```text
Elasticsearch
```

Search receives events such as:

```text
EscrowCreated
EscrowUpdated
EscrowFunded
DisputeOpened
PayoutCompleted
```

and creates searchable projections.

Search is not authoritative.

---

# 20. Bounded Context: Audit

Audit captures security-sensitive and business-sensitive history.

It may consume events from Kafka.

Examples:

```text
UserAuthenticated
EscrowCreated
TermsAccepted
FundingConfirmed
FundsReleased
DisputeResolved
AdminAdjustmentCreated
```

Audit data should be:

* Immutable
* Searchable
* Durable
* Retained according to policy

---

# 21. Bounded Context: Reconciliation

Reconciliation is responsible for detecting disagreement between internal and external financial records.

Examples:

```text
Internal:
Payment = PROCESSING

Provider:
Payment = SUCCESS
```

or:

```text
Internal payout = SUCCESS
Provider settlement file has no payout
```

Reconciliation must identify and resolve these inconsistencies safely.

---

# 22. Aggregate Boundaries

Initial aggregates:

```text
Identity
└── User

Escrow
└── Escrow

Payments
└── Payment

Ledger
├── LedgerAccount
└── Journal

Disputes
└── Dispute

Payout
└── Payout

Messaging
└── Conversation
```

The aggregate root controls changes inside its consistency boundary.

---

# 23. What Belongs Inside an Aggregate?

Data should normally belong inside one aggregate when it must remain transactionally consistent with that aggregate.

Example:

```text
Escrow
├── current state
├── buyer ID
├── seller ID
├── current terms version
└── version
```

These fields frequently participate in the same business decision.

Payment provider attempts do not need to live inside Escrow.

Therefore:

```text
Escrow
```

and:

```text
Payment
```

are separate aggregates.

---

# 24. Aggregate Size

Aggregates should remain relatively small.

Do not load:

```text
Escrow
+ 10,000 messages
+ 500 audit records
+ 100 payment attempts
+ 50 evidence files
```

into one JPA object graph.

That creates:

* Large queries
* Memory pressure
* Lock contention
* Slow transactions
* Difficult scaling

Use identifiers between aggregates.

Example:

```text
Escrow {
    buyerId
    sellerId
}
```

not:

```text
Escrow {
    User buyer;
    User seller;
}
```

across service boundaries.

---

# 25. Service Database Ownership

Each bounded context should eventually own its data.

Conceptually:

```text
Identity DB
Escrow DB
Payment DB
Ledger DB
Dispute DB
Payout DB
Messaging DB
```

This does not necessarily mean seven PostgreSQL servers on day one.

Logical ownership and physical infrastructure are separate decisions.

Early environments may share a PostgreSQL cluster while using separate databases or schemas.

Ownership rules must still be enforced.

---

# 26. Cross-Service Foreign Keys

We should not create database foreign keys across service-owned databases.

Example:

Escrow stores:

```text
buyerId = UUID
```

It does not rely on:

```text
FOREIGN KEY buyerId
REFERENCES identity_database.users
```

The application verifies references through service contracts and business workflows.

---

# 27. Transaction Boundaries

A database transaction should not normally span multiple services.

Bad conceptual approach:

```text
BEGIN TRANSACTION

Update escrow DB
Update payment DB
Update ledger DB

COMMIT
```

Distributed systems cannot safely depend on ordinary local ACID transactions across independently deployed databases.

Instead, we use:

```text
Local transaction
+
events
+
idempotency
+
saga/workflow
```

---

# 28. Example Funding Workflow

Consider:

```text
Buyer funds escrow
```

Possible workflow:

```text
Client
  ↓
Payment Service
  ↓
Provider
  ↓
Payment SUCCEEDED
  ↓
PaymentSucceeded event
  ↓
Ledger Service
  ↓
Post funding journal
  ↓
EscrowFundingSecured event
  ↓
Escrow Service
  ↓
FUNDED
```

Several systems participate.

There is no single distributed database transaction covering all of them.

That means we must design for partial failure.

---

# 29. Partial Failure Example

Suppose:

```text
Payment succeeds
```

but:

```text
Ledger service is unavailable
```

We now have:

```text
Provider:
SUCCESS

Payment:
SUCCESS

Ledger:
not yet posted

Escrow:
FUNDING_PROCESSING
```

This is not automatically corruption.

It is an intermediate distributed state.

Kafka retains the event.

The ledger consumer retries.

Eventually:

```text
Ledger:
funding journal committed

Escrow:
FUNDED
```

If automatic recovery fails, reconciliation detects it.

---

# 30. Synchronous Communication

Synchronous calls are appropriate when the caller needs an immediate answer.

Examples:

```text
Escrow Service
    ↓
Identity Service
"Is this user currently eligible?"
```

or:

```text
API Gateway
    ↓
Identity
Validate token
```

Possible technologies:

```text
HTTP REST
gRPC
```

We will start primarily with HTTP and later evaluate gRPC for internal high-throughput interactions.

---

# 31. Asynchronous Communication

Asynchronous communication is appropriate when:

* Immediate response is unnecessary.
* Several consumers care about an event.
* Coupling should be reduced.
* Work should survive temporary consumer failure.
* Event replay is useful.

Example:

```text
EscrowReleased
      ↓ Kafka
      ├── Notification
      ├── Search
      ├── Analytics
      ├── Audit
      └── Risk
```

The Escrow service does not need to synchronously call five services.

---

# 32. Kafka Usage

Kafka is preferred for durable domain facts.

Examples:

```text
EscrowCreated
TermsAccepted
PaymentSucceeded
FundingConfirmed
DisputeOpened
EscrowReleased
PayoutCompleted
```

Characteristics:

* Multiple consumers
* Durable history
* Replay useful
* High throughput
* Partitioned ordering

---

# 33. RabbitMQ Usage

RabbitMQ is preferred for work distribution.

Examples:

```text
SendEmail
GenerateReceipt
ScanEvidence
ProcessImage
RetryPayoutProviderCall
GenerateStatement
```

Characteristics:

* Worker should perform a task.
* Competing consumers are useful.
* Queue depth represents pending work.
* Retry and dead-letter routing are important.

---

# 34. Redis Usage

Redis will support:

* Cache
* Rate limiting
* Session state
* Short-lived data
* Fraud velocity counters
* Idempotency acceleration
* Distributed coordination where justified

Redis must not become the only source of truth for financial state.

---

# 35. Elasticsearch Usage

Elasticsearch will support:

* Full-text search
* Transaction discovery
* Dispute investigation
* Administrative filtering
* User search
* Aggregations

Data reaches Elasticsearch asynchronously.

Example:

```text
EscrowUpdated
   ↓ Kafka
Search Indexer
   ↓
Elasticsearch
```

---

# 36. Initial Candidate Microservices

The bounded contexts suggest these eventual services:

```text
identity-service

escrow-service

payment-service

ledger-service

payout-service

dispute-service

risk-service

messaging-service

notification-service

search-indexer

reconciliation-service

api-gateway
```

However:

```text
bounded context
```

does not automatically mean:

```text
deployable microservice immediately
```

We will split where independent ownership, scaling, reliability, or deployment justifies it.

---

# 37. Services We Definitely Want Separate

The strongest early candidates are:

```text
identity-service
escrow-service
payment-service
ledger-service
```

Why?

Because they have clearly different responsibilities.

## Escrow Service

```text
What should happen?
```

## Payment Service

```text
Did external funding succeed?
```

## Ledger Service

```text
What happened to the money internally?
```

## Identity Service

```text
Who is performing the action?
```

These should not collapse into one giant service.

---

# 38. Ledger Must Be Highly Isolated

Ledger deserves especially strong isolation.

Reasons include:

* Financial correctness
* Strict database access
* Smaller attack surface
* Independent performance tuning
* Controlled deployment
* Strong audit requirements

Few services should be allowed to issue commands to the ledger.

No service should directly modify ledger tables.

---

# 39. Search Should Be Independent

Search workloads are fundamentally different from transactional workloads.

PostgreSQL workload:

```text
small indexed transactional queries
```

Elasticsearch workload:

```text
full-text
filters
aggregations
large search result spaces
```

Combining these concerns would force one system to optimise for incompatible workloads.

---

# 40. Messaging Should Scale Independently

Messaging can eventually become one of the platform's largest workloads.

Example:

```text
10 million active users
× multiple messages/day
```

This workload should not compete with ledger operations for:

* Threads
* database connections
* CPU
* memory
* storage

---

# 41. Data Consistency Categories

Not all domains require the same consistency.

## Strong consistency

Examples:

```text
Ledger postings
Available balance
Escrow state transition
Idempotency records
Refund limits
Payout reservations
```

## Eventual consistency

Examples:

```text
Search index
Dashboard totals
Notifications
Analytics
Risk projections
Activity feeds
```

This distinction is fundamental to scalability.

---

# 42. Source of Truth Matrix

| Data             | Authoritative Owner |
| ---------------- | ------------------- |
| User identity    | Identity            |
| Escrow agreement | Escrow              |
| Escrow state     | Escrow              |
| Payment attempt  | Payment             |
| Ledger balance   | Ledger              |
| Payout           | Payout              |
| Dispute          | Dispute             |
| Message          | Messaging           |
| Search document  | Derived             |
| Cached escrow    | Derived             |
| Analytics        | Derived             |

---

# 43. Domain Event Ownership

A service publishes events only for facts it owns.

Examples:

Payment owns:

```text
PaymentSucceeded
PaymentFailed
```

Escrow owns:

```text
EscrowCreated
EscrowFunded
EscrowReleased
```

Ledger owns:

```text
JournalPosted
FundsReserved
FundsReleased
```

Dispute owns:

```text
DisputeOpened
DisputeResolved
```

This prevents unclear ownership.

---

# 44. Commands Versus Events

A command asks for something to happen.

Example:

```text
ReleaseEscrowFunds
```

An event states that something happened.

Example:

```text
EscrowFundsReleased
```

Commands can fail.

Events describe committed facts.

Do not name a command:

```text
FundsReleased
```

before the funds actually release.

---

# 45. Example Command Flow

Buyer accepts delivery:

```text
AcceptDelivery
      ↓
Escrow Service
      ↓
Validate:
state = INSPECTION
no dispute
buyer authorised
      ↓
RELEASE_PENDING
      ↓
ReleaseRequested
```

Then:

```text
ReleaseRequested
      ↓
Ledger workflow
      ↓
Post balanced journal
      ↓
FundsReleased
      ↓
Escrow Service
      ↓
RELEASED
```

---

# 46. Why We Do Not Use Distributed Transactions

Imagine:

```text
Escrow database
Ledger database
Payment database
```

A traditional transaction cannot simply guarantee:

```text
all three commit together
```

without introducing heavy distributed transaction protocols.

Instead we prefer:

```text
Local ACID transactions
+
Transactional outbox
+
Kafka
+
Idempotent consumers
+
Reconciliation
```

This will become one of the central patterns in the project.

---

# 47. Transactional Outbox

When an Escrow operation changes state and needs to publish an event:

Bad:

```text
UPDATE escrow

commit

publish Kafka event
```

The application may crash between:

```text
commit
```

and:

```text
publish
```

Then the state changed but no event exists.

Instead:

```text
BEGIN

UPDATE escrow

INSERT INTO outbox_event

COMMIT
```

A publisher later sends the outbox record to Kafka.

This ensures:

```text
Business state
+
event intent
```

are committed together.

---

# 48. Consumer Inbox

Consumers also need protection against duplicate messages.

Example:

```text
FundsReleased event
```

arrives twice.

The consumer stores:

```text
consumerName
eventId
processedAt
```

with a unique constraint.

Conceptually:

```text
UNIQUE(consumer_name, event_id)
```

This helps create idempotent consumers.

---

# 49. Domain Model and Database Technology

Initial storage direction:

```text
Identity          → PostgreSQL
Escrow            → PostgreSQL
Payments          → PostgreSQL
Ledger            → PostgreSQL
Payout            → PostgreSQL
Disputes          → PostgreSQL
Messaging         → PostgreSQL initially
Cache             → Redis
Search            → Elasticsearch
Events            → Kafka
Work queues        → RabbitMQ
Files             → Object storage
```

We may later change storage technologies when workload evidence justifies it.

---

# 50. Design Principles

The system will follow these principles:

```text
Business capability before service name.

One authoritative owner for important data.

Local ACID transactions.

No cross-service database writes.

Events for durable business facts.

Commands for requested actions.

Kafka for event distribution.

RabbitMQ for work queues.

Redis for temporary fast access.

Elasticsearch for derived search.

PostgreSQL for authoritative transactional data.

Financial correctness over convenience.

Idempotency everywhere financial effects occur.

Partial failure is normal.

Observability is part of architecture.
```

---

# 51. Initial Architecture Boundary

Our first implementation will likely begin with:

```text
API Gateway
    |
    +-------------------+
    |                   |
Identity Service     Escrow Service
                        |
                        |
                  Payment Service
                        |
                        |
                   Ledger Service
```

Asynchronous infrastructure:

```text
                     Kafka
                      |
          +-----------+-----------+
          |           |           |
       Search      Audit        Risk
          |
    Elasticsearch
```

Background work:

```text
RabbitMQ
   |
   +── Email workers
   +── Receipt workers
   +── Evidence workers
```

Redis will be shared infrastructure initially but logically separated by key namespaces and ownership.

---

# 52. First Implementation Boundary

The first end-to-end workflow will involve:

```text
Identity
Escrow
Payment
Ledger
```

We will not initially implement:

```text
Full dispute service
Full search service
Full risk engine
Full messaging platform
Multi-region deployment
```

But the architecture must allow them to be added without rewriting ownership rules.

---

# 53. Next Document

The next document should be:

```text
docs/architecture/high-level-architecture.md
```

That document will convert these bounded contexts into a concrete system architecture covering:

* API Gateway
* Internal service communication
* Kafka
* RabbitMQ
* Redis
* Elasticsearch
* PostgreSQL
* Object storage
* Load balancing
* Service discovery
* Observability
* Database ownership
* Failure boundaries
* Deployment topology
