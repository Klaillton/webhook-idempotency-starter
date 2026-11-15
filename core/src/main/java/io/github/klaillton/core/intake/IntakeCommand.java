package io.github.klaillton.core.intake;

import java.time.Duration;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;

/**
 * One delivery after the caller has chosen the extractor value.
 * A null or blank {@code key} means the extractor found nothing.
 */
public record IntakeCommand(
        String source,
        String key,
        byte[] body,
        int bodyLimitBytes,
        SignatureScheme scheme,
        String signatureHeader,
        List<SigningSecret> secrets,
        Duration timestampTolerance,
        Duration lease) {

    public IntakeCommand {
        Objects.requireNonNull(source, "source");
        Objects.requireNonNull(body, "body");
        Objects.requireNonNull(scheme, "scheme");
        Objects.requireNonNull(secrets, "secrets");
        Objects.requireNonNull(lease, "lease");
        if (source.isBlank()) {
            throw new IllegalArgumentException("source is required");
        }
        if (bodyLimitBytes < 0) {
            throw new IllegalArgumentException("body limit must be zero or positive");
        }
        if (lease.isZero() || lease.isNegative()) {
            throw new IllegalArgumentException("lease must be positive");
        }
        body = body.clone();
        secrets = List.copyOf(secrets);
    }

    @Override
    public byte[] body() {
        return body.clone();
    }

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof IntakeCommand command)) {
            return false;
        }
        return sameDelivery(command) && samePolicy(command);
    }

    private boolean sameDelivery(IntakeCommand other) {
        return source.equals(other.source)
                && Objects.equals(key, other.key)
                && Arrays.equals(body, other.body)
                && bodyLimitBytes == other.bodyLimitBytes
                && scheme == other.scheme;
    }

    private boolean samePolicy(IntakeCommand other) {
        return Objects.equals(signatureHeader, other.signatureHeader)
                && secrets.equals(other.secrets)
                && Objects.equals(timestampTolerance, other.timestampTolerance)
                && lease.equals(other.lease);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                source,
                key,
                Arrays.hashCode(body),
                bodyLimitBytes,
                scheme,
                signatureHeader,
                secrets,
                timestampTolerance,
                lease);
    }

    @Override
    public String toString() {
        return "IntakeCommand[source="
                + source
                + ", key="
                + key
                + ", bodyLength="
                + body.length
                + ", bodyLimitBytes="
                + bodyLimitBytes
                + ", scheme="
                + scheme
                + ", lease="
                + lease
                + "]";
    }
}
