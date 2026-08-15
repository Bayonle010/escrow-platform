# ADR 0004: Relational and Document Data Ownership

**Status:** Accepted

**Date:** 2026-08-15

## Context

The project will explore both relational and document databases. Each database must be selected for
a real data requirement without weakening financial correctness.

## Decision

PostgreSQL will remain the authoritative transactional database for:

- users and authentication identities;
- escrows and state transitions;
- payments and idempotency records;
- ledger journals, entries, and balances;
- transactional outbox and consumer inbox records.

These workloads require transactions, constraints, concurrency control, and strong consistency.

MongoDB will be introduced later for dispute-evidence metadata whose structure varies by evidence
type. Images, videos, and other large evidence files will live in object storage; MongoDB will store
their metadata and object references.

The following ownership rules apply:

- MongoDB is not a source of financial truth.
- A financial decision must not depend solely on a MongoDB document.
- Services must not create one transaction spanning PostgreSQL and MongoDB.
- Cross-database workflows will use events and explicit eventual consistency.
- Database unavailability must delay dependent work rather than produce a guessed outcome.
- Each database collection or table has one authoritative service owner.

MongoDB will not be added to the initial funding slice because that workflow has no justified
document-database requirement.

## Alternatives Considered

- **PostgreSQL only:** simpler, and JSONB could support flexible metadata, but it would not provide
  the intended document-database learning path.
- **MongoDB for financial records:** rejected because the ledger and balance model relies on strong
  relational constraints and transactional invariants.
- **MongoDB in the first slice:** rejected because adding unused infrastructure creates complexity
  without solving a current problem.

## Consequences

- Financial workflows retain PostgreSQL's transactional guarantees.
- The project gains a justified MongoDB use case and can compare document modelling with JSONB.
- Cross-database data will be eventually consistent and require retry and reconciliation behaviour.
- Dispute evidence may be unavailable while core financial records remain safe.
