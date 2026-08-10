# Escrow Platform Product Requirements Document

**Version:** 1.0
**Status:** Draft
**Project Type:** Large-Scale Distributed Systems Engineering Project
**Primary Stack:** Java / Spring Ecosystem
**Target Scale:** 100 million registered users
**Peak Stress Objective:** Up to 1 million incoming requests per second

---

# 1. Product Overview

The Escrow Platform is a general-purpose financial transaction platform that allows buyers and sellers to transact without requiring either party to fully trust the other.

The platform temporarily secures funds from a buyer while a seller fulfils an agreed obligation.

After successful fulfilment, the funds are released to the seller.

If the parties disagree, the transaction enters a dispute process where the funds remain protected until the issue is resolved.

The platform should support transactions involving:

* Physical goods
* Digital products
* Freelance services
* Professional services
* Marketplace transactions
* Peer-to-peer transactions
* Business-to-business transactions
* Milestone-based work

---

# 2. Engineering Purpose

This project also serves as a large-scale backend engineering environment.

The architecture should expose engineers to the kinds of problems encountered in systems operating at companies such as:

* Large fintech platforms
* Global marketplaces
* Large social platforms
* Large e-commerce platforms
* Transportation platforms
* Payment processors

The goal is not merely to build working APIs.

The system must provide practical experience with:

* Distributed systems
* High-throughput architecture
* Microservices
* Event-driven systems
* Kafka
* RabbitMQ
* Redis
* Elasticsearch
* PostgreSQL at large scale
* Database connection management
* Data partitioning
* Sharding
* Replication
* Idempotency
* Distributed transactions
* Sagas
* Transactional outbox
* Eventual consistency
* Financial ledgers
* Concurrency
* Distributed locking
* Caching
* Load balancing
* Rate limiting
* Backpressure
* Resilience
* Observability
* Docker
* Kubernetes
* Infrastructure as Code
* CI/CD
* Large-scale testing
* Production incident reasoning

---

# 3. Problem Statement

Transactions between buyers and sellers contain trust problems.

A buyer may fear that:

* The seller will disappear after payment.
* The seller will not deliver.
* The delivered item will differ from the agreement.
* The service will be incomplete.
* Obtaining a refund will be difficult.

A seller may fear that:

* The buyer will receive the product and refuse payment.
* The buyer will make a false non-delivery claim.
* The buyer will abuse the dispute system.
* The buyer will attempt payment reversal after fulfilment.

The platform must reduce these risks by becoming a trusted intermediary.

---

# 4. Product Vision

Build a global escrow infrastructure capable of securely coordinating transactions between buyers, sellers, marketplaces, and businesses at very large scale.

The long-term platform should support:

```text
100 million+ registered users

millions of daily transactions

large marketplace integrations

multiple countries

multiple currencies

multiple payment providers

multiple payout providers

high-volume API integrations

regional deployments
```

The platform should eventually be usable through:

* Consumer applications
* Marketplace APIs
* Merchant APIs
* Hosted escrow checkout
* SDKs
* Business integrations

---

# 5. Primary Product Goals

## 5.1 Buyer Protection

Protect buyers against:

* Non-delivery
* Fraudulent sellers
* Misrepresented products
* Incomplete services
* Unauthorized fund release

---

## 5.2 Seller Protection

Protect sellers against:

* Buyers refusing payment after delivery
* False non-delivery claims
* Fraudulent disputes
* Duplicate cancellation attempts
* Unjustified payment reversals

---

## 5.3 Financial Correctness

The system must ensure:

```text
Money cannot accidentally be created.

Money cannot silently disappear.

Funds cannot be released twice.

Refunds cannot occur twice.

Payouts cannot occur twice.

An escrow cannot spend more than it holds.

Ledger debits must equal ledger credits.
```

Financial correctness has higher priority than latency.

---

## 5.4 Availability

The platform must remain operational during:

* Individual service failures
* Cache failures
* Search failures
* Notification failures
* Broker failures
* Provider outages
* Application instance failures

Where safe, the system should degrade functionality instead of completely failing.

---

## 5.5 Scalability

The architecture must support horizontal scaling of:

* API traffic
* Escrow processing
* Payments
* Ledger operations
* Search
* Messaging
* Notifications
* Event consumers
* Background workers

---

# 6. User Types

The platform supports the following actors.

## Buyer

The buyer:

* Creates or accepts escrow agreements.
* Funds transactions.
* Reviews delivery.
* Accepts fulfilment.
* Opens disputes.
* Receives refunds.

---

## Seller

The seller:

* Creates or accepts escrow agreements.
* Delivers goods or services.
* Submits delivery evidence.
* Receives released funds.
* Receives payouts.
* Responds to disputes.

---

## Marketplace

A marketplace may:

* Create transactions through APIs.
* Connect its own buyers and sellers.
* Receive platform commissions.
* Receive transaction webhooks.
* Manage transactions according to granted permissions.

---

## Dispute Officer

Responsible for:

* Reviewing cases.
* Reviewing evidence.
* Requesting additional evidence.
* Resolving disputes.
* Making split decisions.

---

## Compliance Officer

Responsible for:

* Identity reviews.
* Risk investigations.
* Account restrictions.
* Transaction monitoring.
* Compliance holds.

---

## Finance Operations Officer

Responsible for:

* Reconciliation.
* Failed payments.
* Failed payouts.
* Suspense balances.
* Provider mismatches.
* Manual financial investigation.

---

## Platform Administrator

Responsible for:

* Configuration
* Limits
* Fees
* Supported currencies
* Provider routing
* Roles
* Operational controls

Administrative users must not directly modify financial balances.

---

# 7. Primary Escrow Journey

The basic transaction lifecycle is:

```text
Buyer/Seller creates escrow
        ↓
Counterparty reviews terms
        ↓
Both parties accept terms
        ↓
Buyer funds escrow
        ↓
Payment confirmed
        ↓
Funds secured in ledger
        ↓
Seller fulfils transaction
        ↓
Seller submits delivery
        ↓
Buyer inspects delivery
        ↓
Buyer accepts
        ↓
Funds released
        ↓
Seller payout
        ↓
Transaction completed
```

Alternative path:

```text
Delivery
   ↓
Buyer disputes
   ↓
Funds frozen
   ↓
Evidence submitted
   ↓
Dispute reviewed
   ↓
Release / Refund / Split
```

---

# 8. Escrow Creation Requirements

An escrow must contain:

* Escrow ID
* Buyer
* Seller
* Transaction description
* Amount
* Currency
* Transaction category
* Terms version
* Delivery deadline
* Inspection period
* Release conditions
* Refund conditions
* Creation timestamp

Optional future fields include:

* Milestones
* Marketplace reference
* External order reference
* Shipping requirements
* Evidence requirements

---

# 9. Terms Requirements

Escrow terms are versioned.

Example:

```text
Terms v1
    ↓ changed
Terms v2
```

Acceptance must reference a specific version.

If terms change:

```text
previous acceptance becomes invalid
```

Both parties must accept the latest version.

---

# 10. Funding Requirements

After terms acceptance, the buyer can fund the escrow.

Supported funding mechanisms may eventually include:

* Bank transfer
* Card
* Internal balance
* Open banking
* Mobile money
* Region-specific providers

The payment layer must be provider-independent.

---

# 11. Payment Confirmation

Client-side confirmation is insufficient.

The platform must not trust:

* Browser redirects
* Mobile success screens
* Screenshots
* Uploaded payment receipts
* Unsigned callbacks

Funding must be confirmed through:

* Authenticated provider webhook
* Provider status API
* Settlement/reconciliation data

---

# 12. Payment Idempotency

Duplicate provider notifications must not duplicate funding.

Example:

```text
PaymentSucceeded
PaymentSucceeded
PaymentSucceeded
```

must create:

```text
one financial funding effect
```

---

# 13. Escrow Fund Storage

Funds must be represented through the internal financial ledger.

The ledger must support:

* Held funds
* Available seller balance
* Refund obligations
* Fees
* Pending payouts
* Provider clearing
* Suspense accounts

The ledger is the source of truth for financial balances.

---

# 14. Fulfilment Requirements

After funding, the seller can begin fulfilment.

The seller may submit:

* Completion notes
* Tracking number
* Shipping provider
* Documents
* Images
* Files
* External references
* Delivery timestamps

Large files must be stored in object storage.

---

# 15. Inspection Requirements

Following delivery, the buyer receives a defined inspection period.

The buyer may:

```text
Accept delivery

Open dispute

Request correction

Take no action
```

If configured, inspection expiry may initiate automatic release.

Before automatic release, the system must revalidate:

* Current escrow state
* Active disputes
* Holds
* Risk restrictions
* Available held amount

---

# 16. Release Requirements

Funds may be released when:

* Buyer accepts delivery.
* Inspection period expires.
* Dispute officer resolves in favour of seller.
* Milestone acceptance occurs.

Before release, the system must confirm:

```text
escrow is funded

escrow is not disputed

escrow is not frozen

seller is eligible

funds exist

release has not already occurred
```

---

# 17. Fees

The platform may support:

* Escrow fee
* Funding fee
* Payment-processing fee
* Payout fee
* Currency conversion fee
* Marketplace commission
* Dispute fee

Fee allocation may be:

* Buyer pays
* Seller pays
* Shared
* Marketplace pays

Fees must be deterministically calculated and auditable.

---

# 18. Refund Requirements

The platform must support:

* Full refunds
* Partial refunds
* Refund after cancellation
* Refund after dispute
* Refund after failed fulfilment

A refund must never exceed the remaining refundable balance.

---

# 19. Dispute Requirements

Either participant may open a dispute where permitted.

A dispute contains:

* Reason
* Description
* Requested resolution
* Evidence
* Responses
* Timeline
* Assigned officer
* Decision
* Resolution

When a dispute opens:

```text
automatic release stops
```

Funds remain protected until resolution.

---

# 20. Dispute Outcomes

Possible outcomes:

```text
Full seller release

Full buyer refund

Partial seller release

Partial buyer refund

Split resolution

Request further evidence

Escalation
```

Every resolution must be auditable.

---

# 21. Payout Requirements

After funds become available to the seller, payout may occur.

Payout methods may include:

* Bank transfer
* Internal balance
* Supported regional methods

Payout states may include:

```text
REQUESTED
RESERVED
PROCESSING
SUCCESS
FAILED
UNKNOWN
```

A network timeout must not automatically be treated as payout failure.

---

# 22. Internal Wallet

The platform may expose balances to users.

Important categories include:

```text
Ledger balance

Available balance

Held balance

Reserved balance

Pending payout
```

These values have different meanings.

The system must not expose one generic mutable `balance` column as its only accounting mechanism.

---

# 23. Double-Entry Ledger

Every financial operation must create a balanced journal.

Example:

Buyer funds:

```text
Debit:
Provider clearing

Credit:
Escrow held funds
```

Release:

```text
Debit:
Escrow held funds

Credit:
Seller available balance

Credit:
Platform fee revenue
```

Rule:

```text
total debit = total credit
```

always.

---

# 24. Messaging

Buyer and seller should have transaction-specific communication.

Messaging must support:

* Real-time chat
* Message persistence
* Read state
* Attachments
* System-generated messages
* Moderation
* Evidence preservation

Messaging should scale separately from financial workloads.

---

# 25. Notifications

The platform should support:

* Email
* SMS
* Push
* In-app notifications
* Partner webhooks

Notifications may be triggered by events such as:

```text
EscrowCreated

TermsAccepted

EscrowFunded

DeliverySubmitted

DisputeOpened

EscrowReleased

RefundCompleted

PayoutCompleted
```

Notification failure must not invalidate successful financial operations.

---

# 26. Search Requirements

Users and administrative staff require search across:

* Escrows
* Users
* Payments
* Disputes
* Payouts

Search may support:

* Full-text search
* Date filters
* Status filters
* Amount filters
* Currency
* Buyer
* Seller
* Transaction category

Elasticsearch will provide derived search projections.

It is not the source of transactional truth.

---

# 27. Authentication Requirements

The system must support:

* Registration
* Email verification
* Phone verification
* Login
* Password reset
* MFA
* Session management
* Token revocation
* Device tracking
* Business API credentials

---

# 28. Authorization Requirements

Authorization must operate at both:

```text
role level
```

and:

```text
resource level
```

For example:

A user may be generally authenticated but still cannot:

```text
AcceptDelivery
```

unless they are the buyer for that escrow.

---

# 29. Risk Requirements

Risk checks may consider:

* Transaction amount
* Account age
* Transaction velocity
* IP address
* Device
* Historical disputes
* Payment history
* Payout destination changes
* Linked accounts
* Geographic anomalies

Possible decisions:

```text
ALLOW
REVIEW
HOLD
REJECT
```

---

# 30. Compliance Requirements

The architecture must support configurable:

* KYC
* KYB
* Transaction monitoring
* User limits
* Country restrictions
* Sanctions checks
* Account restrictions
* Transaction holds
* Record retention

Legal requirements differ by jurisdiction and must not be hard-coded globally.

---

# 31. Reconciliation Requirements

The platform must reconcile internal records against:

* Payment providers
* Payout providers
* Bank settlement records
* Fee records

Example:

```text
Internal payment:
PROCESSING

Provider:
SUCCESS
```

Reconciliation should identify and safely repair the inconsistency.

---

# 32. Administrative Requirements

Authorized administrative staff must be able to:

* Search transactions
* Search users
* View timelines
* Investigate payments
* Investigate payouts
* Investigate disputes
* View ledger records
* Freeze transactions
* Restrict accounts
* Trigger controlled reconciliation
* Create controlled financial adjustments

Direct database modification must not be part of normal operations.

---

# 33. API Requirements

External APIs should support:

* REST
* API versioning
* Consistent error format
* Authentication
* Authorization
* Idempotency keys
* Pagination
* Rate limiting
* Correlation IDs
* Webhook signatures

Marketplace integrations may later receive SDKs.

---

# 34. Idempotency Requirements

Every externally triggered financial operation must support idempotency.

Examples:

```text
Fund escrow

Release funds

Refund

Payout

Adjustment
```

Retries must not create duplicate financial effects.

---

# 35. Non-Functional Scale Requirements

Long-term targets:

```text
100 million registered users

30 million monthly active users

10 million daily active users

2 million concurrently active users

up to 1 million incoming requests/sec under stress

up to 100,000+ domain events/sec under peak production assumptions
```

Exact capacity requirements remain defined in:

```text
docs/capacity-model.md
```

---

# 36. Availability Requirements

Target availability:

| Capability           | Target |
| -------------------- | -----: |
| Authentication       | 99.99% |
| Escrow critical API  | 99.99% |
| Financial operations | 99.99% |
| Messaging            |  99.9% |
| Search               |  99.9% |
| Admin                |  99.9% |

---

# 37. Latency Requirements

Initial targets:

| Operation                           |  p95 target |
| ----------------------------------- | ----------: |
| Cached read                         |    < 100 ms |
| Standard read                       |    < 250 ms |
| Internal state change               |    < 500 ms |
| Financial operation acknowledgement |  < 1 second |
| Search                              |    < 750 ms |
| Real-time message delivery          | < 2 seconds |

External provider latency must be measured separately.

---

# 38. Distributed-System Requirements

The system must tolerate:

* Duplicate requests
* Duplicate messages
* Out-of-order messages
* Service crashes
* Consumer crashes
* Network timeouts
* Kafka temporary outages
* RabbitMQ temporary outages
* Redis outages
* Elasticsearch outages
* Database failover
* External provider uncertainty
* Delayed callbacks

Partial failure is considered normal.

---

# 39. Event-Driven Requirements

Important business events include:

```text
UserRegistered

EscrowCreated

EscrowTermsAccepted

PaymentSucceeded

EscrowFundingSecured

EscrowFunded

DeliverySubmitted

DisputeOpened

EscrowFundsReleased

EscrowReleased

PayoutSucceeded
```

Kafka will distribute durable domain facts.

---

# 40. Background Work Requirements

RabbitMQ will handle worker-oriented tasks such as:

```text
SendEmail

SendSMS

GenerateReceipt

ScanEvidence

GenerateStatement

RetryExternalTask
```

Kafka and RabbitMQ must not be used interchangeably without architectural justification.

---

# 41. Cache Requirements

Redis may be used for:

* Read caching
* Rate limiting
* Fraud velocity counters
* Temporary verification data
* Session-related data
* Short-lived coordination

Redis failure must reduce performance rather than corrupt financial truth.

---

# 42. Search Requirements

Elasticsearch supports derived read models.

The system must support:

* Eventual consistency
* Index replay
* Reindexing
* Stable IDs
* Search projection versions
* Shard scaling
* Index lifecycle management

---

# 43. Database Requirements

PostgreSQL is the initial authoritative relational database.

The architecture must support:

* Database ownership
* Index optimisation
* Connection pooling
* Read replicas
* Partitioning
* Archival
* Sharding when justified
* Large tables
* Billions of ledger entries

Database connections must be budgeted globally.

---

# 44. Security Requirements

The platform requires:

* TLS
* Encryption at rest
* Secure secret management
* MFA for sensitive accounts
* Strong authorization
* Audit logs
* Webhook verification
* Replay protection
* Rate limiting
* Secure file handling
* Dependency security scanning
* Least privilege

---

# 45. Audit Requirements

Every important action must be traceable.

Examples:

```text
Who created the escrow?

Who changed the terms?

Who accepted?

What payment funded it?

What ledger journal secured it?

Who submitted delivery?

Who opened the dispute?

Who released the money?

What payout moved the funds externally?
```

---

# 46. Observability Requirements

The platform must provide:

* Structured logs
* Metrics
* Distributed tracing
* Correlation IDs
* Kafka lag
* RabbitMQ queue depth
* Database connection metrics
* Redis metrics
* Elasticsearch metrics
* Business metrics

OpenTelemetry will be the primary instrumentation standard.

---

# 47. Failure Isolation Requirements

Optional system failure should not stop core financial processing unnecessarily.

Examples:

```text
Search unavailable
→ advanced search unavailable
→ escrow still operates
```

```text
Notification unavailable
→ communication delayed
→ funding still succeeds
```

```text
Redis unavailable
→ slower requests
→ authoritative state remains correct
```

Critical ledger unavailability should cause financial operations to fail safely.

---

# 48. Testing Requirements

The system requires:

* Unit tests
* Integration tests
* Database integration tests
* Testcontainers
* Contract tests
* Event contract tests
* Consumer tests
* Concurrency tests
* Load tests
* Stress tests
* Spike tests
* Soak tests
* Failure-injection tests
* Chaos tests

---

# 49. Required Concurrency Scenarios

Tests must cover:

```text
Two releases simultaneously

Two refunds simultaneously

Two payouts against one balance

Accept delivery versus open dispute

Automatic release versus dispute

Duplicate payment confirmation

Duplicate Kafka event

Consumer crash after database commit

Out-of-order provider webhook
```

---

# 50. Deployment Requirements

Development will initially use:

```text
Docker Compose
```

Production-style architecture will later use:

```text
Docker

Kubernetes

Terraform

CI/CD
```

Services must support horizontal scaling and graceful shutdown.

---

# 51. Engineering Workflow Requirements

Development will use:

```text
protected main branch

short-lived feature branches

pull requests

automated tests

code review

architecture decision records

CI quality checks
```

Architecture decisions must be documented instead of remaining tribal knowledge.

---

# 52. Core Technology Direction

Current technology direction:

```text
Java

Spring Boot

Spring Security

Spring Cloud Gateway

PostgreSQL

Kafka

RabbitMQ

Redis

Elasticsearch

Flyway

HikariCP

Docker

Kubernetes

Terraform

OpenTelemetry

Prometheus

Grafana
```

Technology inclusion does not automatically justify its usage.

Every technology must solve a measurable problem.

---

# 53. Initial Implementation Scope

The first vertical slice will focus on:

```text
Register user
        ↓
Create escrow
        ↓
Counterparty accepts terms
        ↓
Initiate funding
        ↓
Payment succeeds
        ↓
Ledger secures funds
        ↓
Escrow becomes FUNDED
```

This limited business slice must still implement production-oriented patterns including:

* Service ownership
* Database transactions
* Kafka
* Transactional outbox
* Consumer idempotency
* Idempotency keys
* Observability
* Database connection pooling
* Distributed failure handling

---

# 54. Out of Initial Scope

The first implementation does not need to immediately support:

* Every country
* Every currency
* Full milestone escrow
* Crypto custody
* International FX
* Complex marketplace commission models
* Active-active multi-region writes
* Machine-learning fraud detection
* Full dispute automation

These capabilities should remain architecturally possible.

---

# 55. Success Metrics

## Product

* Escrows created
* Escrows funded
* Completion rate
* Dispute rate
* Refund rate
* Transaction volume
* User retention

## Financial

* Funds held
* Funds released
* Fees
* Reconciliation differences
* Failed payouts

## Technical

* Request throughput
* p95/p99 latency
* Error rate
* Kafka lag
* RabbitMQ queue depth
* DB connection saturation
* Cache hit rate
* Search lag
* Recovery time

Critical financial metric:

```text
ledger imbalance count = 0
```

---

# 56. Core Product Invariants

The product must always preserve:

```text
No accidental creation of money.

No silent loss of money.

No duplicate funding.

No duplicate release.

No duplicate refund.

No duplicate payout.

No release of disputed funds.

No spending above available balance.

Every financial journal balances.

Every financial operation is auditable.
```

Detailed invariants are maintained in:

```text
docs/invariants.md
```

---

# 57. Related Design Documents

This PRD should be read alongside:

```text
docs/capacity-model.md

docs/invariants.md

docs/state-machine.md

docs/domain-model.md

docs/architecture/high-level-architecture.md

docs/architecture/command-event-catalogue.md
```

Further architecture documents will define implementation details.

---

# 58. Remaining Pre-Build Documents

Before the first application code is written, the following core designs should be completed:

```text
Data and Ledger Architecture

API Design Specification

Service Communication Strategy

Kafka Topology

Security Architecture

Reliability and Failure Model

Observability Architecture

Testing Strategy

First Vertical Slice Design
```

Other specialised documents such as Redis, Elasticsearch, RabbitMQ, Kubernetes, and multi-region design may be completed immediately before those technologies enter implementation.

---

# 59. Product Principle

The escrow product provides the business problem.

The engineering project provides the learning environment.

The primary technical question throughout development is not:

```text
Does the endpoint work?
```

It is:

```text
Does the system remain correct when
traffic is high,
requests are concurrent,
messages are duplicated,
services crash,
networks time out,
and infrastructure partially fails?
```
