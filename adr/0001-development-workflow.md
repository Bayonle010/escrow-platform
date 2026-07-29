# ADR 0001: Development Workflow

**Status:** Accepted  
**Date:** 2026-07-29

## Context

The escrow platform is being built as a production-grade distributed system.

The project must simulate the engineering practices used by teams working on large-scale systems. Changes should therefore be reviewed, tested, and integrated frequently.

## Decision

The project will use trunk-based development with short-lived feature branches.

The `main` branch will remain protected and deployable.

All meaningful changes will follow this process:

1. Create a short-lived branch from `main`.
2. Make one focused change.
3. Add or update tests and documentation.
4. Push the branch.
5. Open a pull request.
6. Run automated checks.
7. Review the change.
8. Merge into `main`.
9. Delete the feature branch.

## Branch naming

Examples:

```text
feature/escrow-creation
feature/ledger-posting
fix/duplicate-release
docs/capacity-model
infra/kafka-cluster
test/concurrent-funding