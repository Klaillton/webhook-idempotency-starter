package io.github.klaillton.core.intake;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Stripe {@code Stripe-Signature}. The signed payload is the timestamp, a dot,
 * and the raw body. A timestamp outside the supplied tolerance is rejected.
 */
public final class StripeSignature {

    private StripeSignature() {}

    public static boolean verifies(
            byte[] body, String header, List<SigningSecret> secrets, Duration tolerance, Instant now) {
        if (tolerance == null) {
            throw new IllegalArgumentException("timestamp tolerance is required for Stripe");
        }
        var parsed = parse(header);
        if (parsed == null || !withinTolerance(parsed.timestamp(), tolerance, now)) {
            return false;
        }
        var payload = signedPayload(parsed.timestamp(), body);
        var matched = false;
        for (var secret : secrets) {
            if (!secret.activeAt(now)) {
                continue;
            }
            for (var signature : parsed.signatures()) {
                matched |= HmacSha256.matches(secret.key(), payload, signature);
            }
        }
        return matched;
    }

    private static boolean withinTolerance(long timestamp, Duration tolerance, Instant now) {
        var skew = Duration.between(Instant.ofEpochSecond(timestamp), now).abs();
        return skew.compareTo(tolerance) <= 0;
    }

    private static byte[] signedPayload(long timestamp, byte[] body) {
        var prefix = (timestamp + ".").getBytes(StandardCharsets.US_ASCII);
        var payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        return payload;
    }

    private static Parsed parse(String header) {
        if (header == null || header.isBlank()) {
            return null;
        }
        Long timestamp = null;
        var signatures = new ArrayList<byte[]>();
        for (var part : header.split(",")) {
            var pair = part.trim().split("=", 2);
            if (pair.length != 2) {
                continue;
            }
            if ("t".equals(pair[0])) {
                timestamp = parseTimestamp(pair[1]);
            } else if ("v1".equals(pair[0])) {
                var decoded = HexBytes.decode(pair[1]);
                if (decoded != null) {
                    signatures.add(decoded);
                }
            }
        }
        if (timestamp == null || signatures.isEmpty()) {
            return null;
        }
        return new Parsed(timestamp, List.copyOf(signatures));
    }

    private static Long parseTimestamp(String value) {
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private record Parsed(long timestamp, List<byte[]> signatures) {}
}
