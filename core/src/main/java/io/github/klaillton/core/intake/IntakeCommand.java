package io.github.klaillton.core.intake;

import java.time.Duration;
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
}
