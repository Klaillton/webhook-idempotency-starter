# ADR-0002: Closed decisions for Gauntlet S3 and S4

## Status

Accepted — 2026-11-15

## Context

The webhook-idempotency-starter is under the Gauntlet ADD-POLY framework. S1 is implemented. S3 and S4 were still open decisions that needed to be closed before the Grok CLI advances those stages.

## Decision

### Baseline and migration requirement

- The starter requires Spring Boot 3.5.x + Java 21 (Jakarta EE / `jakarta.*` namespace).
- Existing applications on Spring Boot 2.7/2.8 (`javax.*` namespace, Java 8/11 baseline) are incompatible.
- The javax → jakarta migration and Java 17+ baseline are mandatory before the starter can be adopted. Mixing the two stacks fails at compile time or on the classpath.
- Target for adoption: Spring Boot 3.5.x + Java 21 first. Java 25 and Spring Boot 4 are deferred to a later phase.

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

### S4 — Cucumber + Testcontainers, openapi-diff

- **Cucumber + Testcontainers**
  - Minimum scenarios (all with real Postgres via Testcontainers, Gherkin in `features/`):
    1. New webhook → process and store response
    2. Same idempotency key → replay original response, do not reprocess
    3. Invalid HMAC → reject and do not store
    4. Failure mid-transaction → rollback; second delivery reprocesses
    5. Concurrent redelivery of the same key (included early)
  - Additional desirable scenarios (HMAC + altered payload, large/special response) and product-dependent ones (rate limit, multi-tenant, key expiry, 5xx vs 4xx) are backlog and do not block S4

- **openapi-diff**
  - The current `openapi/openapi.yaml` is the official baseline (source of truth).
  - Diffs are taken against the version on the base branch.
  - Breaking changes (removed fields, type changes, mandatory status code changes) fail the gate; non-breaking changes (optional fields, new endpoints) pass.
  - Rule is active as of 2026-11-15. The gate itself runs when the Gauntlet runner implements openapi-diff (still listed as not yet in ADOPT-STATUS).

## Consequences

- The Grok CLI can now treat these values as fixed policy for S3 and S4
- Any change to these thresholds or rules requires a new ADR and `policy-change-approved`
- Adoption of the starter is blocked until the consuming application has completed the Spring Boot 2.x → 3.5 + Java 21 migration
