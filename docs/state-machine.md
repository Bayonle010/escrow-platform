# Escrow Platform State Machine

**Version:** 1.0
**Status:** Draft
**System:** General-Purpose Escrow Platform

---

## 1. Purpose

This document defines the lifecycle of an escrow transaction.

An escrow transaction must always exist in one valid state.

Every state transition must define:

* Current state
* Command
* Actor
* Preconditions
* Resulting state
* Financial effect
* Domain events
* Failure behaviour

The state machine exists to prevent invalid operations such as:

```text
Unfunded escrow → Released
Disputed escrow → Automatically released
Refunded escrow → Funded again
Cancelled escrow → Delivered
```

The lifecycle state is authoritative inside the escrow domain.

Redis, Elasticsearch, Kafka consumers, dashboards, and frontend applications may contain projections of the state, but they are not authoritative.

---

# 2. State Overview

The primary escrow lifecycle is:

```text
DRAFT
  ↓
AWAITING_COUNTERPARTY
  ↓
TERMS_ACCEPTED
  ↓
AWAITING_FUNDING
  ↓
FUNDING_PROCESSING
  ↓
FUNDED
  ↓
IN_PROGRESS
  ↓
DELIVERED
  ↓
INSPECTION
  ↓
RELEASE_PENDING
  ↓
RELEASED
```

Alternative branches include:

```text
CANCELLED
EXPIRED
FUNDING_FAILED
DISPUTED
REFUND_PENDING
REFUNDED
PARTIALLY_RELEASED
PARTIALLY_REFUNDED
ON_HOLD
PAYOUT_PENDING
PAYOUT_FAILED
COMPLETED
```

---

# 3. State Categories

States are grouped into the following categories.

## 3.1 Pre-funding states

```text
DRAFT
AWAITING_COUNTERPARTY
TERMS_ACCEPTED
AWAITING_FUNDING
FUNDING_PROCESSING
FUNDING_FAILED
```

## 3.2 Active transaction states

```text
FUNDED
IN_PROGRESS
DELIVERED
INSPECTION
```

## 3.3 Financial settlement states

```text
RELEASE_PENDING
PARTIALLY_RELEASED
REFUND_PENDING
PARTIALLY_REFUNDED
PAYOUT_PENDING
```

## 3.4 Exception states

```text
DISPUTED
ON_HOLD
PAYOUT_FAILED
```

## 3.5 Terminal states

```text
RELEASED
REFUNDED
CANCELLED
EXPIRED
COMPLETED
```

---

# 4. DRAFT

## Meaning

The escrow creator is preparing the transaction.

The counterparty has not yet been formally invited.

The creator may still modify the terms.

Examples of editable fields include:

* Description
* Amount
* Currency
* Delivery deadline
* Inspection period
* Counterparty
* Milestones
* Release conditions

## Allowed actors

* Escrow creator

## Allowed commands

```text
UpdateEscrowDraft
DeleteEscrowDraft
SubmitEscrow
```

## Transition

```text
DRAFT
  ↓ SubmitEscrow
AWAITING_COUNTERPARTY
```

## Preconditions

Before submission:

* Buyer must exist.
* Seller must exist or be invitable.
* Buyer and seller must be different parties.
* Amount must be valid.
* Currency must be supported.
* Terms must pass validation.
* Transaction category must be allowed.

## Financial effect

None.

## Events

```text
EscrowDraftUpdated
EscrowSubmitted
```

`EscrowDraftUpdated` may not need to be globally published unless another system requires it.

---

# 5. AWAITING_COUNTERPARTY

## Meaning

The escrow terms have been submitted and one party is waiting for the other party to review them.

## Allowed actors

* Buyer
* Seller
* Escrow creator
* Invited counterparty

## Allowed commands

```text
AcceptTerms
RejectTerms
ProposeTermsChange
CancelEscrow
```

## Accepted transition

```text
AWAITING_COUNTERPARTY
  ↓ AcceptTerms
TERMS_ACCEPTED
```

## Rejected transition

```text
AWAITING_COUNTERPARTY
  ↓ RejectTerms
CANCELLED
```

Alternatively, future product versions may allow rejection to return the escrow for editing.

## Terms change

```text
AWAITING_COUNTERPARTY
  ↓ ProposeTermsChange
AWAITING_COUNTERPARTY
```

A terms change creates a new terms version.

All earlier acceptance becomes invalid.

## Preconditions for acceptance

* User must be the invited counterparty.
* User must be authorised.
* Terms version must match the latest version.
* Escrow must not be expired.
* Escrow must not be cancelled.

## Events

```text
EscrowTermsAccepted
EscrowTermsRejected
EscrowTermsChanged
EscrowCancelled
```

---

# 6. TERMS_ACCEPTED

## Meaning

Both parties have accepted the same version of the escrow terms.

The transaction is now ready for funding.

The system may immediately transition to `AWAITING_FUNDING`.

Conceptually:

```text
TERMS_ACCEPTED
  ↓ PrepareFunding
AWAITING_FUNDING
```

This transition may occur automatically inside the same command.

## Allowed actors

* System

## Financial effect

None.

## Events

```text
EscrowFullyAccepted
FundingRequired
```

---

# 7. AWAITING_FUNDING

## Meaning

The transaction is ready for the buyer to provide funds.

The seller must not begin fulfilment yet.

## Allowed actors

* Buyer
* System
* Payment service

## Allowed commands

```text
InitiateFunding
CancelEscrow
ExpireEscrow
```

## Funding transition

```text
AWAITING_FUNDING
  ↓ InitiateFunding
FUNDING_PROCESSING
```

## Cancellation

```text
AWAITING_FUNDING
  ↓ CancelEscrow
CANCELLED
```

## Expiration

```text
AWAITING_FUNDING
  ↓ ExpireEscrow
EXPIRED
```

## Preconditions for funding

* Escrow terms remain valid.
* Buyer is authorised.
* Currency is supported.
* Escrow is not expired.
* Required compliance checks have passed.
* Funding instruction does not already exist or is safely retryable.

## Events

```text
FundingInitiated
EscrowCancelled
EscrowExpired
```

---

# 8. FUNDING_PROCESSING

## Meaning

A payment attempt exists, but the platform has not yet confirmed that funds are securely available.

Examples:

* Bank transfer pending
* Card authorization processing
* Payment provider callback pending
* Provider response unknown
* Reconciliation required

The seller must still not begin fulfilment.

## Allowed actors

* Payment service
* Reconciliation service
* System

## Commands

```text
ConfirmFunding
FailFunding
MarkFundingUnknown
RetryFundingVerification
```

## Successful transition

```text
FUNDING_PROCESSING
  ↓ ConfirmFunding
FUNDED
```

## Failed transition

```text
FUNDING_PROCESSING
  ↓ FailFunding
FUNDING_FAILED
```

## Important rule

A frontend success response must never cause this transition:

```text
FUNDING_PROCESSING → FUNDED
```

Funding confirmation requires trusted server-side verification.

## Financial effect

`ConfirmFunding` must atomically or reliably cause:

```text
Buyer / provider clearing account
        ↓
Escrow held account
```

through the ledger.

The escrow must not become financially funded without a committed ledger journal.

## Events

```text
FundingConfirmed
FundingFailed
FundingVerificationRequired
```

---

# 9. FUNDING_FAILED

## Meaning

A funding attempt failed and no valid escrow funds were secured.

## Allowed actors

* Buyer
* System

## Commands

```text
RetryFunding
CancelEscrow
```

## Retry transition

```text
FUNDING_FAILED
  ↓ RetryFunding
FUNDING_PROCESSING
```

## Cancel transition

```text
FUNDING_FAILED
  ↓ CancelEscrow
CANCELLED
```

## Financial effect

No escrow balance should have been created from the failed attempt.

If provider state is unknown, the transaction must not enter `FUNDING_FAILED`.

It should remain under investigation or reconciliation.

---

# 10. FUNDED

## Meaning

The required funds have been confirmed and secured.

The seller can safely begin fulfilment.

## Allowed actors

* Seller
* System
* Risk service
* Compliance service

## Commands

```text
StartFulfilment
PlaceOnHold
OpenDispute
```

## Normal transition

```text
FUNDED
  ↓ StartFulfilment
IN_PROGRESS
```

## Hold transition

```text
FUNDED
  ↓ PlaceOnHold
ON_HOLD
```

## Preconditions

* Required funded amount exists.
* Ledger confirms the escrow-held funds.
* Escrow is not frozen.
* Seller is eligible to fulfil.

## Events

```text
EscrowFunded
FulfilmentStarted
EscrowPlacedOnHold
```

---

# 11. IN_PROGRESS

## Meaning

The seller is currently delivering the product or service.

## Allowed actors

* Seller
* Buyer
* System
* Risk/compliance staff

## Commands

```text
SubmitDelivery
OpenDispute
RequestCancellation
PlaceOnHold
```

## Delivery transition

```text
IN_PROGRESS
  ↓ SubmitDelivery
DELIVERED
```

## Events

```text
DeliverySubmitted
DisputeOpened
EscrowPlacedOnHold
```

## Financial effect

Normally none.

Funds remain in the escrow-held balance.

---

# 12. DELIVERED

## Meaning

The seller claims to have fulfilled the transaction.

Delivery evidence may include:

* Tracking number
* Completion notes
* Files
* Signed document
* Product identifiers
* External references

The system prepares the buyer inspection period.

## Transition

```text
DELIVERED
  ↓ BeginInspection
INSPECTION
```

This transition may occur immediately after successful delivery submission.

## Events

```text
DeliveryConfirmedBySeller
InspectionStarted
```

---

# 13. INSPECTION

## Meaning

The buyer has a defined period to inspect the delivered product or service.

## Allowed actors

* Buyer
* System
* Risk/compliance staff

## Commands

```text
AcceptDelivery
OpenDispute
RequestCorrection
ExpireInspection
PlaceOnHold
```

## Buyer acceptance

```text
INSPECTION
  ↓ AcceptDelivery
RELEASE_PENDING
```

## Automatic acceptance

If configured:

```text
INSPECTION
  ↓ InspectionPeriodExpired
RELEASE_PENDING
```

Before automatic release, the system must revalidate:

* Current state
* Active disputes
* Account restrictions
* Compliance holds
* Remaining releasable balance

## Dispute

```text
INSPECTION
  ↓ OpenDispute
DISPUTED
```

## Events

```text
DeliveryAccepted
InspectionPeriodExpired
DisputeOpened
CorrectionRequested
```

---

# 14. RELEASE_PENDING

## Meaning

The business decision to release funds has been approved, but the financial journal may not yet have completed.

This intermediate state is important.

We do not want:

```text
INSPECTION
  ↓
RELEASED
```

before the ledger actually commits.

## Allowed actors

* Ledger workflow
* System
* Reconciliation process

## Commands

```text
CommitRelease
FailRelease
```

## Successful transition

```text
RELEASE_PENDING
  ↓ CommitRelease
RELEASED
```

## Partial release

```text
RELEASE_PENDING
  ↓ CommitPartialRelease
PARTIALLY_RELEASED
```

## Financial effect

Example:

```text
Debit: Escrow held         £100
Credit: Seller available    £98
Credit: Platform revenue     £2
```

## Events

```text
ReleaseRequested
LedgerReleaseCommitted
EscrowReleased
EscrowPartiallyReleased
```

---

# 15. PARTIALLY_RELEASED

## Meaning

Only part of the escrow funds have been released.

Example:

```text
Funded:       £1,000
Released:       £400
Remaining:      £600
```

This may occur because of:

* Milestones
* Partial delivery
* Dispute resolution
* Agreed partial release

## Allowed commands

```text
ReleaseRemainingFunds
RefundRemainingFunds
OpenDispute
CompleteMilestone
```

Possible transitions include:

```text
PARTIALLY_RELEASED
  ↓ ReleaseRemainingFunds
RELEASE_PENDING
```

or:

```text
PARTIALLY_RELEASED
  ↓ RefundRemainingFunds
REFUND_PENDING
```

---

# 16. DISPUTED

## Meaning

The buyer and seller disagree about the outcome of the transaction.

Funds must remain protected.

## Allowed actors

* Buyer
* Seller
* Dispute officer
* System

## Commands

```text
SubmitEvidence
RespondToDispute
ResolveForBuyer
ResolveForSeller
ResolveSplit
EscalateDispute
PlaceOnHold
```

## Important financial rule

While the escrow is disputed:

```text
Automatic release = prohibited
```

Scheduled release jobs must recheck the authoritative state.

## Seller resolution

```text
DISPUTED
  ↓ ResolveForSeller
RELEASE_PENDING
```

## Buyer resolution

```text
DISPUTED
  ↓ ResolveForBuyer
REFUND_PENDING
```

## Split resolution

```text
DISPUTED
  ↓ ResolveSplit
```

This may create:

```text
PARTIALLY_RELEASED
+
PARTIALLY_REFUNDED
```

financial effects.

## Events

```text
DisputeOpened
DisputeEvidenceSubmitted
DisputeEscalated
DisputeResolvedForBuyer
DisputeResolvedForSeller
DisputeResolvedSplit
```

---

# 17. REFUND_PENDING

## Meaning

A refund decision has been approved, but the financial process is not yet complete.

## Allowed actors

* Ledger workflow
* Payment service
* Reconciliation service

## Commands

```text
CommitRefund
FailRefund
MarkRefundUnknown
```

## Successful transition

```text
REFUND_PENDING
  ↓ CommitRefund
REFUNDED
```

## Partial refund

```text
REFUND_PENDING
  ↓ CommitPartialRefund
PARTIALLY_REFUNDED
```

## Financial effect

Depending on funding model:

```text
Debit: Escrow held
Credit: Buyer refundable/available balance
```

or an external provider refund workflow may also be required.

## Events

```text
RefundRequested
RefundCommitted
RefundFailed
RefundOutcomeUnknown
```

---

# 18. PARTIALLY_REFUNDED

## Meaning

Part of the escrow amount has been returned to the buyer.

Example:

```text
Funded:       £1,000
Refunded:       £300
Remaining:      £700
```

The remaining amount may later be:

* Released
* Refunded
* Held
* Resolved through dispute

---

# 19. ON_HOLD

## Meaning

The transaction has been temporarily frozen.

Possible reasons include:

* Fraud investigation
* Compliance review
* Sanctions review
* Administrative investigation
* Legal order
* Provider settlement issue
* Security incident

## Allowed actors

* Risk service
* Compliance service
* Authorised administrator
* System

## Commands

```text
ReleaseHold
CancelTransaction
ForceReview
```

## Important rule

While on hold:

```text
Release prohibited
Refund may require manual approval
Payout prohibited
Automatic scheduled transitions suspended
```

The previous state must be preserved.

Conceptually:

```text
previousState = INSPECTION
currentState = ON_HOLD
```

When the hold is removed:

```text
ON_HOLD
  ↓ ReleaseHold
INSPECTION
```

The platform should not guess the previous state.

It must store it explicitly or model holds separately from lifecycle state.

### Design note

A strong future design may represent:

```text
Lifecycle state = INSPECTION
Restriction state = ON_HOLD
```

instead of making `ON_HOLD` part of the main lifecycle.

This decision will be addressed during domain modelling.

---

# 20. RELEASED

## Meaning

The escrow funds have been fully transferred out of the escrow-held balance according to the accepted outcome.

The seller may now have:

```text
Seller available balance
```

or the platform may proceed directly to external payout.

## Allowed commands

```text
InitiatePayout
CloseEscrow
```

## Important rule

Released does not necessarily mean the seller has received money in an external bank account.

It means the escrow financial obligation has been released.

---

# 21. PAYOUT_PENDING

## Meaning

Seller funds have been released internally and an external payout is being processed.

## Commands

```text
ConfirmPayout
FailPayout
MarkPayoutUnknown
```

## Success

```text
PAYOUT_PENDING
  ↓ ConfirmPayout
COMPLETED
```

## Failure

```text
PAYOUT_PENDING
  ↓ FailPayout
PAYOUT_FAILED
```

## Unknown

The platform may remain in:

```text
PAYOUT_PENDING
```

with a secondary status such as:

```text
providerOutcome = UNKNOWN
```

until reconciliation completes.

---

# 22. PAYOUT_FAILED

## Meaning

The seller has a valid internal balance, but the external payout failed.

This must not undo the completed escrow release.

That distinction is important:

```text
Escrow outcome = RELEASED
Payout outcome = FAILED
```

The seller is still owed the money.

## Commands

```text
RetryPayout
ChangePayoutMethod
ReturnToSellerBalance
```

## Retry

```text
PAYOUT_FAILED
  ↓ RetryPayout
PAYOUT_PENDING
```

---

# 23. RELEASED Versus COMPLETED

These two states have different meanings.

## RELEASED

The escrow obligation has been financially resolved.

```text
Escrow-held funds = 0
Seller entitlement established
```

## COMPLETED

All required downstream work has completed.

Examples:

* Escrow released
* Payout confirmed
* Required receipts generated
* Required settlement records created

This separation prevents external payout problems from incorrectly changing the escrow outcome.

---

# 24. REFUNDED

## Meaning

All refundable escrow funds have been returned to the buyer.

This is a terminal state for the escrow.

## Allowed commands

Normally none.

Administrative recovery actions must use separate financial adjustment workflows.

---

# 25. CANCELLED

## Meaning

The transaction was cancelled before successful completion.

A cancelled escrow must not contain unreconciled customer funds.

If money has already been secured, cancellation must trigger an appropriate refund workflow instead of directly changing the state to `CANCELLED`.

For example, this is dangerous:

```text
FUNDED
  ↓ CancelEscrow
CANCELLED
```

Instead:

```text
FUNDED
  ↓ CancelAndRefund
REFUND_PENDING
  ↓
REFUNDED
```

---

# 26. EXPIRED

## Meaning

The transaction expired because a required action did not occur within the configured period.

Examples:

* Counterparty did not accept terms.
* Buyer did not fund.
* Draft invitation expired.

A funded escrow must never simply expire while holding money.

---

# 27. Full State Transition Table

| Current State         | Command              | Actor            | Next State                |
| --------------------- | -------------------- | ---------------- | ------------------------- |
| DRAFT                 | SubmitEscrow         | Creator          | AWAITING_COUNTERPARTY     |
| DRAFT                 | DeleteDraft          | Creator          | Deleted logically         |
| AWAITING_COUNTERPARTY | AcceptTerms          | Counterparty     | TERMS_ACCEPTED            |
| AWAITING_COUNTERPARTY | RejectTerms          | Counterparty     | CANCELLED                 |
| AWAITING_COUNTERPARTY | CancelEscrow         | Creator          | CANCELLED                 |
| TERMS_ACCEPTED        | PrepareFunding       | System           | AWAITING_FUNDING          |
| AWAITING_FUNDING      | InitiateFunding      | Buyer            | FUNDING_PROCESSING        |
| AWAITING_FUNDING      | CancelEscrow         | Buyer/System     | CANCELLED                 |
| AWAITING_FUNDING      | ExpireEscrow         | System           | EXPIRED                   |
| FUNDING_PROCESSING    | ConfirmFunding       | Payment System   | FUNDED                    |
| FUNDING_PROCESSING    | FailFunding          | Payment System   | FUNDING_FAILED            |
| FUNDING_FAILED        | RetryFunding         | Buyer            | FUNDING_PROCESSING        |
| FUNDING_FAILED        | CancelEscrow         | Buyer            | CANCELLED                 |
| FUNDED                | StartFulfilment      | Seller           | IN_PROGRESS               |
| FUNDED                | PlaceOnHold          | Risk/Compliance  | ON_HOLD                   |
| IN_PROGRESS           | SubmitDelivery       | Seller           | DELIVERED                 |
| IN_PROGRESS           | OpenDispute          | Buyer/Seller     | DISPUTED                  |
| DELIVERED             | BeginInspection      | System           | INSPECTION                |
| INSPECTION            | AcceptDelivery       | Buyer            | RELEASE_PENDING           |
| INSPECTION            | InspectionExpired    | System           | RELEASE_PENDING           |
| INSPECTION            | OpenDispute          | Buyer            | DISPUTED                  |
| RELEASE_PENDING       | CommitRelease        | Ledger           | RELEASED                  |
| RELEASE_PENDING       | CommitPartialRelease | Ledger           | PARTIALLY_RELEASED        |
| PARTIALLY_RELEASED    | ReleaseRemaining     | System/Buyer     | RELEASE_PENDING           |
| PARTIALLY_RELEASED    | RefundRemaining      | Resolution       | REFUND_PENDING            |
| DISPUTED              | ResolveForSeller     | Dispute Officer  | RELEASE_PENDING           |
| DISPUTED              | ResolveForBuyer      | Dispute Officer  | REFUND_PENDING            |
| DISPUTED              | ResolveSplit         | Dispute Officer  | Partial financial outcome |
| REFUND_PENDING        | CommitRefund         | Ledger/Payment   | REFUNDED                  |
| REFUND_PENDING        | CommitPartialRefund  | Ledger           | PARTIALLY_REFUNDED        |
| RELEASED              | InitiatePayout       | System/Seller    | PAYOUT_PENDING            |
| PAYOUT_PENDING        | ConfirmPayout        | Payment Provider | COMPLETED                 |
| PAYOUT_PENDING        | FailPayout           | Payment Provider | PAYOUT_FAILED             |
| PAYOUT_FAILED         | RetryPayout          | Seller/System    | PAYOUT_PENDING            |

---

# 28. Invalid Transition Examples

These must be rejected:

```text
DRAFT → FUNDED

DRAFT → RELEASED

AWAITING_COUNTERPARTY → DELIVERED

AWAITING_FUNDING → RELEASED

FUNDING_FAILED → FUNDED
without new verified funding

IN_PROGRESS → RELEASED
without acceptance/resolution

DISPUTED → automatic RELEASED

REFUNDED → FUNDED

CANCELLED → IN_PROGRESS

EXPIRED → FUNDING_PROCESSING
without an explicit reopen operation

RELEASED → DRAFT

COMPLETED → FUNDED
```

---

# 29. State Transition Concurrency

State transitions must handle concurrent commands.

Example:

```text
Current state = INSPECTION
```

Two requests arrive simultaneously:

```text
Request A:
AcceptDelivery

Request B:
OpenDispute
```

The platform must not successfully commit both incompatible transitions.

A likely implementation will use optimistic versioning.

Example:

```text
Escrow
id = 123
state = INSPECTION
version = 17
```

Both requests read:

```text
version = 17
```

Request A commits:

```text
state = RELEASE_PENDING
version = 18
```

Request B attempts to update:

```text
WHERE id = 123
AND version = 17
```

Affected rows:

```text
0
```

Request B must reload authoritative state and determine the correct outcome.

This prevents silent lost updates.

---

# 30. Scheduled Transition Safety

Scheduled operations include:

* Escrow expiration
* Inspection expiration
* Automatic release
* Evidence deadlines
* Retry jobs

A scheduled command must not assume that the state remains unchanged from when the job was created.

Example:

```text
10:00
Inspection expiration job scheduled

10:01
Buyer opens dispute

10:02
Expiration job executes
```

The job must re-read:

```text
currentState = DISPUTED
```

and must not release the funds.

---

# 31. State and Financial Truth

Lifecycle state and ledger state are related but not identical.

Example:

```text
Escrow state = RELEASE_PENDING
Ledger release = not yet committed
```

After successful ledger posting:

```text
Escrow state = RELEASED
```

The system must never rely solely on an enum to determine money availability.

Before irreversible financial operations, authoritative financial state must be checked.

---

# 32. State Transition Event Model

Each successful transition should produce a domain event.

Example envelope:

```json
{
  "eventId": "uuid",
  "eventType": "EscrowFunded",
  "eventVersion": 1,
  "aggregateId": "escrow-123",
  "aggregateType": "Escrow",
  "aggregateVersion": 7,
  "occurredAt": "timestamp",
  "correlationId": "uuid",
  "causationId": "uuid",
  "payload": {}
}
```

Important fields include:

```text
eventId
aggregateId
aggregateVersion
correlationId
causationId
```

These will later help us handle:

* Duplicate events
* Ordering
* Tracing
* Replay
* Consumer idempotency

---

# 33. State History

The platform should preserve state transition history.

Example:

```text
EscrowStateHistory
------------------
id
escrowId
fromState
toState
command
actorType
actorId
reason
aggregateVersion
correlationId
createdAt
```

This provides:

* Auditability
* Support investigation
* Dispute evidence
* Debugging
* Timeline reconstruction

The current escrow table may still contain:

```text
currentState
```

for efficient access.

---

# 34. State Machine Ownership

The escrow lifecycle should be owned by the escrow domain.

Other services must not independently change it.

For example:

```text
Payment Service
```

must not directly execute:

```sql
UPDATE escrow
SET state = 'FUNDED';
```

Instead:

```text
Payment Service
    ↓ FundingConfirmed event/command
Escrow Domain
    ↓ validates transition
FUNDED
```

Similarly:

```text
Ledger Service
```

must not directly update the escrow database.

---

# 35. State Machine and Events

Not every event should trigger a state transition.

Example:

```text
EmailSent
```

does not affect escrow lifecycle.

Likewise:

```text
SearchDocumentIndexed
```

does not affect escrow lifecycle.

Critical domain events include:

```text
EscrowSubmitted
EscrowTermsAccepted
FundingInitiated
FundingConfirmed
FulfilmentStarted
DeliverySubmitted
DeliveryAccepted
DisputeOpened
DisputeResolved
ReleaseRequested
EscrowReleased
RefundRequested
EscrowRefunded
```

---

# 36. State Machine and RabbitMQ

RabbitMQ jobs must not own escrow lifecycle decisions.

Example:

```text
InspectionExpired
```

may cause a worker to submit:

```text
EvaluateInspectionExpiryCommand
```

The escrow domain then evaluates whether release is currently legal.

RabbitMQ worker:

```text
trigger
```

Escrow domain:

```text
decision maker
```

This prevents stale background jobs from bypassing business rules.

---

# 37. State Machine and Redis

Redis may cache:

```text
EscrowSummary
EscrowCurrentView
```

but a financial command must not make its final decision from cached state.

Example:

```text
Redis says:
INSPECTION
```

but PostgreSQL says:

```text
DISPUTED
```

The release must be rejected.

---

# 38. State Machine and Elasticsearch

Elasticsearch may show:

```text
state = FUNDED
```

while the current authoritative state is:

```text
IN_PROGRESS
```

because indexing is asynchronous.

This is acceptable for search.

It is unacceptable for financial decision-making.

---

# 39. State Versioning

Each escrow aggregate should maintain a monotonically increasing version.

Example:

```text
version = 1
EscrowSubmitted

version = 2
TermsAccepted

version = 3
FundingInitiated

version = 4
FundingConfirmed
```

This supports:

* Optimistic concurrency
* Event ordering
* Projection updates
* Debugging
* Stale-write detection

---

# 40. State Machine Tests

The project must include automated tests for every allowed transition.

Example:

```text
INSPECTION + AcceptDelivery
= RELEASE_PENDING
```

And every forbidden transition:

```text
DRAFT + ReleaseFunds
= rejected
```

Concurrency tests must also exist for conflicting commands.

Examples:

```text
AcceptDelivery vs OpenDispute
ReleaseFunds vs RefundFunds
CancelEscrow vs ConfirmFunding
InspectionExpiry vs OpenDispute
```

---

# 41. Future Milestone Extension

Milestone-based escrow may eventually model:

```text
Escrow
  ├── Milestone 1
  │   FUNDED → DELIVERED → RELEASED
  │
  ├── Milestone 2
  │   FUNDED → DISPUTED
  │
  └── Milestone 3
      AWAITING_FUNDING
```

This means the parent escrow may not have one simple financial state.

Milestones may eventually become their own aggregates.

We will not introduce this complexity into the first implementation until the single-delivery flow is correct.

---

# 42. Key Design Principle

The state machine answers:

```text
What is allowed to happen next?
```

The ledger answers:

```text
What happened to the money?
```

Kafka answers:

```text
What business fact occurred that other systems may care about?
```

RabbitMQ answers:

```text
What background work should a worker perform?
```

Redis answers:

```text
What temporary information can we retrieve quickly?
```

Elasticsearch answers:

```text
What information should be efficiently searchable?
```

These responsibilities must remain separate.

---

# 43. Next Design Document

The next document should define the **domain model and aggregate boundaries**.

It will answer:

```text
What exactly is an Escrow aggregate?

Should Payment belong inside Escrow?
    
Should Ledger be a separate bounded context?

Should Dispute be its own aggregate?

Who owns terms?

Who owns payout state?

Which services may communicate synchronously?

Which relationships use IDs instead of database foreign keys?

Where should transaction boundaries exist?
```

Only after answering those questions should we split the system into microservices.
