-- ADR-0004: dormant in v0.1. The primary key stays (source, key).
ALTER TABLE idempotency_record
    ADD COLUMN tenant_id text;
