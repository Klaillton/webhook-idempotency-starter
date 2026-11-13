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
        if (!(other instanceof IdempotencyRecord(
                String otherSource,
                String otherKey,
                String otherFingerprint,
                Status otherStatus,
                Instant otherLockedUntil,
                byte[] otherResponse))) {
            return false;
        }
        return source.equals(otherSource)
                && key.equals(otherKey)
                && fingerprint.equals(otherFingerprint)
                && status == otherStatus
                && lockedUntil.equals(otherLockedUntil)
                && Arrays.equals(responseBody, otherResponse);
    }

    @Override
    public int hashCode() {
        return java.util.Objects.hash(source, key, fingerprint, status, lockedUntil, Arrays.hashCode(responseBody));
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
                + ", responseLength="
                + responseLength
                + "]";
    }
}
