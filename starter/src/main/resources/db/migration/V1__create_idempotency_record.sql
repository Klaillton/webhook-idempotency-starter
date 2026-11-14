CREATE TABLE idempotency_record (
    source text NOT NULL,
    key text NOT NULL,
    fingerprint text NOT NULL,
    status text NOT NULL,
    locked_until timestamptz NOT NULL,
    response_code integer,
    response_body bytea,
    created_at timestamptz NOT NULL,
    expires_at timestamptz,
    PRIMARY KEY (source, key),
    CONSTRAINT idempotency_record_status_ck CHECK (status IN ('IN_PROGRESS', 'COMPLETED'))
);
