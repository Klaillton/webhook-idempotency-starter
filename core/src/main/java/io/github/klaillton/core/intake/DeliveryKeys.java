package io.github.klaillton.core.intake;

import java.util.Optional;

/**
 * Default extractors. A source with a native id uses that id and does not fall
 * back to {@code Idempotency-Key}. A missing value stays empty: this class does
 * not choose a response for that case.
 */
public final class DeliveryKeys {

    private DeliveryKeys() {}

    public static Optional<String> select(String source, String githubDelivery, String idempotencyKey, byte[] body) {
        return switch (source) {
            case "github" -> nonBlank(githubDelivery);
            case "stripe" -> StripeEventId.topLevel(body);
            default -> nonBlank(idempotencyKey);
        };
    }

    private static Optional<String> nonBlank(String value) {
        if (value == null || value.isBlank()) {
            return Optional.empty();
        }
        return Optional.of(value.trim());
    }
}
