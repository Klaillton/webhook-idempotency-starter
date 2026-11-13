package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;

class SignatureAndKeyTest {

    private static final Instant NOW = Instant.ofEpochSecond(1492774577);
    private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String GITHUB_HEX = "77325902caca812dc259733aacd046b73817372c777b8d95b402647474516e13";
    private static final String STRIPE_HEX = "9e4711d7c8a6df6f6635a71dd3df8fbb6138d4f2807a487e8fed2e4a67af1fa8";

    @Test
    void shouldAcceptGithubSignatureWhenAnUnexpiredSecretMatches() {
        var previous = new SigningSecret("old".getBytes(StandardCharsets.US_ASCII), NOW.minusSeconds(1));
        var current = new SigningSecret("secret".getBytes(StandardCharsets.US_ASCII), null);

        assertTrue(GithubSignature.verifies(BODY, "sha256=" + GITHUB_HEX, List.of(previous, current), NOW));
    }

    @Test
    void shouldRejectGithubSignatureWhenEverySecretIsExpired() {
        var expired = new SigningSecret("secret".getBytes(StandardCharsets.US_ASCII), NOW.minusSeconds(1));

        assertFalse(GithubSignature.verifies(BODY, "sha256=" + GITHUB_HEX, List.of(expired), NOW));
    }

    @Test
    void shouldRejectStripeSignatureWhenTimestampIsOutsideTolerance() {
        var secret = new SigningSecret("whsec".getBytes(StandardCharsets.US_ASCII), null);
        var header = "t=1492774577,v1=" + STRIPE_HEX;

        assertTrue(StripeSignature.verifies(BODY, header, List.of(secret), Duration.ofMinutes(5), NOW));
        assertTrue(StripeSignature.verifies(
                BODY, header, List.of(secret), Duration.ofMinutes(5), NOW.plus(Duration.ofMinutes(5))));
        assertFalse(StripeSignature.verifies(
                BODY, header, List.of(secret), Duration.ofMinutes(5), NOW.plus(Duration.ofMinutes(5)).plusSeconds(1)));
    }

    @Test
    void shouldUseNativeIdWhenSourceHasOne() {
        assertEquals(Optional.of("delivery-1001"), DeliveryKeys.select("github", "delivery-1001", "other-key", BODY));
        assertEquals(Optional.of("evt_1"), DeliveryKeys.select("stripe", null, "other-key", "{\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.of("evt_1"), DeliveryKeys.select("stripe", null, null, "{\"data\":{\"id\":\"inner\"},\"id\":\"evt_1\"}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), DeliveryKeys.select("stripe", null, "other-key", "{\"data\":{\"id\":\"inner\"}}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.empty(), DeliveryKeys.select("stripe", null, "other-key", "{\"id\":1}".getBytes(StandardCharsets.UTF_8)));
        assertEquals(Optional.of("other-key"), DeliveryKeys.select("custom", "delivery-1001", "other-key", BODY));
        assertEquals(Optional.empty(), DeliveryKeys.select("github", "  ", "other-key", BODY));
    }

    @Test
    void shouldHashRawBytes() {
        assertEquals(64, Fingerprint.sha256Hex(BODY).length());
        assertFalse(Fingerprint.sha256Hex(BODY).equals(Fingerprint.sha256Hex(new byte[] {1})));
    }
}
