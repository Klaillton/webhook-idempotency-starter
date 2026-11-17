package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.Arrays;
import java.util.Objects;

public record IdempotencyRecord(
        String source,
        String key,
        String fingerprint,
        Status status,
        Instant lockedUntil,
        byte[] responseBody,
        Instant createdAt) {

    public enum Status {
        IN_PROGRESS,
        COMPLETED
    }

    public IdempotencyRecord {
        createdAt = Objects.requireNonNull(createdAt, "createdAt");
        responseBody = responseBody == null ? null : responseBody.clone();
    }

    @Override
    public byte[] responseBody() {
        return responseBody == null ? null : responseBody.clone();
    }

    IdempotencyRecord takenOver(String newFingerprint, Instant newLockedUntil) {
        return new IdempotencyRecord(source, key, newFingerprint, Status.IN_PROGRESS, newLockedUntil, null, createdAt);
    }

    IdempotencyRecord completed(byte[] body) {
        return new IdempotencyRecord(source, key, fingerprint, Status.COMPLETED, lockedUntil, body, createdAt);
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof IdempotencyRecord record)) {
            return false;
        }
        return sameDelivery(record) && sameStoredResult(record);
    }

    private boolean sameDelivery(IdempotencyRecord other) {
        return source.equals(other.source)
                && key.equals(other.key)
                && fingerprint.equals(other.fingerprint)
                && status == other.status
                && lockedUntil.equals(other.lockedUntil)
                && createdAt.equals(other.createdAt);
    }

    private boolean sameStoredResult(IdempotencyRecord other) {
        return Arrays.equals(responseBody, other.responseBody);
    }

    @Override
    public int hashCode() {
        return Objects.hash(source, key, fingerprint, status, lockedUntil, createdAt, Arrays.hashCode(responseBody));
    }

    @Override
    public String toString() {
        var responseLength = responseBody == null ? 0 : responseBody.length;
        return "IdempotencyRecord[source="
                + source
                + ", key="
                + key
                + ", fingerprint="
                + fingerprint
                + ", status="
                + status
                + ", lockedUntil="
                + lockedUntil
                + ", createdAt="
                + createdAt
                + ", responseLength="
                + responseLength
                + "]";
    }
}
