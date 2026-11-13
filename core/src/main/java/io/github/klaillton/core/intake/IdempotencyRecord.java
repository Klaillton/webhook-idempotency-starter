package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.Arrays;

public record IdempotencyRecord(
        String source,
        String key,
        String fingerprint,
        Status status,
        Instant lockedUntil,
        byte[] responseBody) {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public IdempotencyRecord {
        responseBody = responseBody == null ? null : responseBody.clone();
    }

    @Override
    public byte[] responseBody() {
        return responseBody == null ? null : responseBody.clone();
    }

    IdempotencyRecord takenOver(String newFingerprint, Instant newLockedUntil) {
        return new IdempotencyRecord(source, key, newFingerprint, Status.IN_PROGRESS, newLockedUntil, null);
    }

    IdempotencyRecord completed(byte[] body) {
        return new IdempotencyRecord(source, key, fingerprint, Status.COMPLETED, lockedUntil, body);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof IdempotencyRecord record)) {
            return false;
        }
        return source.equals(record.source)
                && key.equals(record.key)
                && fingerprint.equals(record.fingerprint)
                && status == record.status
                && lockedUntil.equals(record.lockedUntil)
                && Arrays.equals(responseBody, record.responseBody);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(source, key, fingerprint, status, lockedUntil, Arrays.hashCode(responseBody));
    }
}
