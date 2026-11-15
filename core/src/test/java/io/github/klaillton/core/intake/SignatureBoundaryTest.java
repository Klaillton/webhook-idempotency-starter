package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class SignatureBoundaryTest {

    private static final Instant NOW = Instant.ofEpochSecond(1492774577);
    private static final byte[] BODY = "{}".getBytes(StandardCharsets.UTF_8);
    private static final String STRIPE_HEX = "9e4711d7c8a6df6f6635a71dd3df8fbb6138d4f2807a487e8fed2e4a67af1fa8";

    @Test
    void shouldRejectGithubSignatureWhenTheHeaderIsNotSha256Hex() {
        var secret = List.of(new SigningSecret("secret".getBytes(StandardCharsets.US_ASCII), null));

        assertFalse(GithubSignature.verifies(BODY, null, secret, NOW));
        assertFalse(GithubSignature.verifies(BODY, "SHA256=" + "ab".repeat(32), secret, NOW));
        assertFalse(GithubSignature.verifies(BODY, "sha256=", secret, NOW));
        assertFalse(GithubSignature.verifies(BODY, "sha256=abc", secret, NOW));
        assertFalse(GithubSignature.verifies(BODY, "sha256=zz", secret, NOW));
        assertFalse(GithubSignature.verifies(BODY, "sha256=" + "ab".repeat(16), secret, NOW));
    }

    @Test
    void shouldRejectHmacWhenTheProvidedMacIsWrongLengthOrDoesNotMatch() {
        var key = "secret".getBytes(StandardCharsets.US_ASCII);

        assertFalse(HmacSha256.matches(key, BODY, new byte[31]));
        assertFalse(HmacSha256.matches(key, BODY, new byte[32]));
        assertFalse(HmacSha256.matches(key, BODY, new byte[33]));
    }

    @Test
    void shouldDecodeBothHexCasesAndRejectInvalidDigits() {
        assertEquals(Optional.empty(), HexBytes.decode(null));
        assertEquals(Optional.empty(), HexBytes.decode("abc"));
        assertEquals(Optional.empty(), HexBytes.decode("0G"));
        assertEquals(Optional.empty(), HexBytes.decode("gg"));
        assertArrayEquals(new byte[] {0x09, (byte) 0xaf}, HexBytes.decode("09af").orElseThrow());
        assertArrayEquals(new byte[] {(byte) 0xAF, (byte) 0x09}, HexBytes.decode("AF09").orElseThrow());
        assertArrayEquals(HexBytes.decode("aF").orElseThrow(), HexBytes.decode("Af").orElseThrow());
    }

    @Test
    void shouldTreatTheLimitAsInclusiveWhenComparingBodyLength() {
        assertFalse(BodyLimit.exceeds(new byte[0], 0));
        assertFalse(BodyLimit.exceeds(new byte[3], 3));
        assertTrue(BodyLimit.exceeds(new byte[1], 0));
        assertTrue(BodyLimit.exceeds(new byte[4], 3));
    }

    @Test
    void shouldRejectStripeSignatureWhenNothingMatchesInsideTolerance() {
        var active = new SigningSecret("whsec".getBytes(StandardCharsets.US_ASCII), null);
        var expired = new SigningSecret("whsec".getBytes(StandardCharsets.US_ASCII), NOW.minusSeconds(1));
        var wrong = "t=1492774577,v1=" + "00".repeat(32);
        var good = "t=1492774577,v1=" + STRIPE_HEX;

        assertFalse(StripeSignature.verifies(BODY, wrong, List.of(active), Duration.ofMinutes(5), NOW));
        assertFalse(StripeSignature.verifies(BODY, good, List.of(expired), Duration.ofMinutes(5), NOW));
        assertFalse(StripeSignature.verifies(BODY, " ", List.of(active), Duration.ofMinutes(5), NOW));
        assertThrows(
                IllegalArgumentException.class,
                () -> StripeSignature.verifies(BODY, good, List.of(active), null, NOW));
    }

    @Test
    void shouldRejectStripeSignatureWhenTheTimestampIsNotANumber() {
        var secret = new SigningSecret("whsec".getBytes(StandardCharsets.US_ASCII), null);
        var header = "t=not-a-number,v1=" + hmacHex(secret.key(), signed("0", BODY));

        assertFalse(StripeSignature.verifies(BODY, header, List.of(secret), Duration.ofMinutes(5), Instant.EPOCH));
    }

    private static byte[] signed(String timestamp, byte[] body) {
        var prefix = (timestamp + ".").getBytes(StandardCharsets.US_ASCII);
        var payload = new byte[prefix.length + body.length];
        System.arraycopy(prefix, 0, payload, 0, prefix.length);
        System.arraycopy(body, 0, payload, prefix.length, body.length);
        return payload;
    }

    private static String hmacHex(byte[] key, byte[] payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
