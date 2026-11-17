# ADR-0004: Product decisions for v0.1 (missing key, tenant, retention)

## Status

Accepted — 2026-11-17

## Context

Section 10 of ARCHITECTURE.md listed three open product questions that blocked contract completeness and future schema evolution: missing idempotency key behavior, tenant isolation, and response-body retention. These are now closed for v0.1.

The same text was first committed as `docs/adr/ADR-0003-product-decisions-v0.1.md`. That number already belonged to the PIT equivalent-mutant approval. This ADR is the accepted copy. The earlier file is superseded and is not deleted.

## Decision

### 1. Missing idempotency key

- Default behavior: reject with HTTP 400 when the configured extractor for the source finds no key.
- Nothing is written to the store. Rejection happens before the transaction.
- Per-source override to "process without idempotency" is allowed in the future, but no source has that flag in v0.1.
- 400 is used (not 401 or 422) because those statuses are already reserved for HMAC failure and fingerprint mismatch.

### 2. Tenant

- v0.1 key remains `(source, key)`. `tenant_id` is not part of the primary key.
- A nullable `tenant_id text` column (default null) is added to the schema as a dormant base.
- The column is unused in v0.1. Activation later requires only code and contract changes, not a structural migration.
- Multi-tenant isolation remains the application's responsibility (separate schema, datasource, or filter) until an explicit future ADR changes this.

### 3. Retention

- Idempotency key TTL: default 7 days (`expires_at = created_at + 7 days`). Configurable per source later.
- Response body retention: default 24 hours. After that, `response_body` becomes NULL; the row stays COMPLETED.
- Replay after body expiry returns the original status with an empty body.
- Stored body size limit: 64 KB. Larger bodies are stored as NULL while the delivery is still marked COMPLETED.
- Cleanup job that deletes rows with `expires_at < now()` is deferred to the project S4 (retention work). The column is populated; the job can be added later with an explicit skip reason.

## Consequences

- OpenAPI and Gherkin can now describe the missing-key case as 400.
- Schema gains a nullable `tenant_id` column; primary key stays `(source, key)`.
- `expires_at` is no longer left null by default; body nulling after 24 h is the defined policy.
- Any change to these rules requires a new ADR and `policy-change-approved`.

## Discarded

- Treating a missing key as 401 or 422. Those statuses stay reserved for HMAC failure and fingerprint mismatch.
- Processing the delivery without idempotency in v0.1. That override can exist later, per source, and no source has the flag now.
- Putting `tenant_id` in the primary key. The v0.1 key stays `(source, key)`.
- Leaving `expires_at` null, truncating an oversized body, or deleting expired rows in this version. The row is kept until the deferred cleanup job. An oversized body is stored as NULL and the row stays COMPLETED.
- Keeping two accepted ADR-0003 files. The product text moved here.
