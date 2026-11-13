package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.Arrays;

/** One HMAC secret. A null expiry means the secret does not expire. */
public record SigningSecret(byte[] key, Instant expiresAt) {

    public SigningSecret {
        key = key.clone();
    }

    public boolean activeAt(Instant now) {
        return expiresAt == null || !now.isAfter(expiresAt);
    }

    @Override
    public byte[] key() {
        return key.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof SigningSecret(byte[] otherKey, Instant otherExpiresAt))) {
            return false;
        }
        return Arrays.equals(key, otherKey) && java.util.Objects.equals(expiresAt, otherExpiresAt);
    }

    @Override
    public int hashCode() {
        return 31 * Arrays.hashCode(key) + java.util.Objects.hashCode(expiresAt);
    }

    @Override
    public String toString() {
        return "SigningSecret[keyLength=" + key.length + ", expiresAt=" + expiresAt + "]";
    }
}
