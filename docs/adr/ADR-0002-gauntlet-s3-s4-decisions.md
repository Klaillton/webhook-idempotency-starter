# ADR-0002: Closed decisions for Gauntlet S3 and S4

## Status

Accepted — 2026-11-15

## Context

The webhook-idempotency-starter is under the Gauntlet ADD-POLY framework. S1 is implemented. S3 and S4 were still open decisions that needed to be closed before the Grok CLI advances those stages.

## Decision

### S3 — PIT, CRAP, ArchUnit

- **PIT (mutation testing)**
  - Kill score threshold: 80 %
  - Scope: only the `core` module
  - Equivalent mutants count as killed only after explicit human approval with a recorded reason

- **CRAP**
  - Ceiling: ≤ 8 on touched methods in the `core` module
  - Calculated from JaCoCo coverage + cyclomatic complexity per method
  - Scope: only the `core` module

- **ArchUnit**
  - Domain package of the `core` module must not import Spring, JDBC, servlet APIs, or classes from the `starter` module
  - Fail-closed: any violation fails the gate
  - Layers: domain (pure), adapters (may depend on domain), starter (autoconfiguration only)

### S4 — Cucumber + Testcontainers

- Minimum scenarios (all with real Postgres via Testcontainers, Gherkin in `features/`):
  1. New webhook → process and store response
  2. Same idempotency key → replay original response, do not reprocess
  3. Invalid HMAC → reject and do not store
  4. Failure mid-transaction → rollback; second delivery reprocesses
  5. Concurrent redelivery of the same key (included early)
- Additional desirable scenarios (HMAC + altered payload, large/special response) and product-dependent ones (rate limit, multi-tenant, key expiry, 5xx vs 4xx) are backlog and do not block S4

## Consequences

- The Grok CLI can now treat these values as fixed policy for S3 and the Cucumber part of S4
- Any change to these thresholds or rules requires a new ADR and `policy-change-approved`
- openapi-diff remains the only S4 item still open
