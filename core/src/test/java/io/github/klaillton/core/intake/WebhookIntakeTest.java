package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.junit.jupiter.api.Test;

class WebhookIntakeTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");
    private static final byte[] RESULT = {7, 8, 9};
    private static final String SECRET = "secret";

    private final MemoryIdempotencyStore store = new MemoryIdempotencyStore();
    private final WebhookIntake intake = new WebhookIntake(store, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void shouldReturnHandlerResultWhenDeliveryIsNew() {
        var calls = new AtomicInteger();
        var decision = intake.receive(command(body("{}"), "delivery-1001", github(body("{}"))), event -> {
            calls.incrementAndGet();
            return new WebhookResult(RESULT);
        });

        var completed = assertInstanceOf(IntakeDecision.Completed.class, decision);
        assertFalse(completed.replay());
        assertArrayEquals(RESULT, completed.body());
        assertEquals(1, calls.get());
        assertEquals(IdempotencyRecord.Status.COMPLETED, store.find("github", "delivery-1001").orElseThrow().status());
        assertEquals(200, IntakeDecision.httpStatus(decision).orElseThrow());
    }

    @Test
    void shouldReplayStoredResultWhenBodyIsUnchanged() {
        var calls = new AtomicInteger();
        var command = command(body("{}"), "delivery-1001", github(body("{}")));
        intake.receive(command, event -> {
            calls.incrementAndGet();
            return new WebhookResult(RESULT);
        });

        var decision = intake.receive(command, event -> {
            calls.incrementAndGet();
            return new WebhookResult(new byte[] {0});
        });

        var completed = assertInstanceOf(IntakeDecision.Completed.class, decision);
        assertTrue(completed.replay());
        assertArrayEquals(RESULT, completed.body());
        assertEquals(1, calls.get());
    }

    @Test
    void shouldStoreNothingWhenSignatureDoesNotVerify() {
        var decision = intake.receive(
                command(body("{}"), "delivery-1001", "sha256=" + "0".repeat(64)), event -> new WebhookResult(RESULT));

        assertEquals(IntakeDecision.Reason.SIGNATURE, assertInstanceOf(IntakeDecision.Rejected.class, decision).reason());
        assertEquals(401, IntakeDecision.httpStatus(decision).orElseThrow());
        assertTrue(store.find("github", "delivery-1001").isEmpty());
    }

    @Test
    void shouldLeaveStoredResultWhenFingerprintDiffers() {
        intake.receive(command(body("{}"), "delivery-1001", github(body("{}"))), event -> new WebhookResult(RESULT));
        var other = body("{\"n\":2}");
        var decision = intake.receive(command(other, "delivery-1001", github(other)), event -> new WebhookResult(new byte[] {4}));

        assertEquals(IntakeDecision.Reason.FINGERPRINT, assertInstanceOf(IntakeDecision.Rejected.class, decision).reason());
        assertEquals(422, IntakeDecision.httpStatus(decision).orElseThrow());
        assertArrayEquals(RESULT, store.find("github", "delivery-1001").orElseThrow().responseBody());
    }

    @Test
    void shouldReportWholeSecondsWhenLeaseIsStillValid() {
        var lockedUntil = NOW.plusSeconds(10).plusMillis(500);
        store.insertInProgress("github", "delivery-1001", "old", lockedUntil);
        var payload = body("{}");

        var decision = intake.receive(command(payload, "delivery-1001", github(payload)), event -> {
            throw new AssertionError("handler must not run while the lease is valid");
        });

        var inProgress = assertInstanceOf(IntakeDecision.InProgress.class, decision);
        assertEquals(11, inProgress.retryAfterSeconds());
        assertEquals(409, IntakeDecision.httpStatus(decision).orElseThrow());
        assertEquals("old", store.find("github", "delivery-1001").orElseThrow().fingerprint());
    }

    @Test
    void shouldRejectOversizedBodyBeforeSignature() {
        var calls = new AtomicInteger();
        var payload = new byte[] {1, 2};
        var decision = intake.receive(
                new IntakeCommand(
                        "github",
                        "delivery-1001",
                        payload,
                        1,
                        SignatureScheme.GITHUB,
                        "not-a-signature",
                        List.of(new SigningSecret(SECRET.getBytes(StandardCharsets.US_ASCII), null)),
                        null,
                        Duration.ofMinutes(1)),
                event -> {
                    calls.incrementAndGet();
                    return new WebhookResult(RESULT);
                });

        assertEquals(IntakeDecision.Reason.BODY_TOO_LARGE, assertInstanceOf(IntakeDecision.Rejected.class, decision).reason());
        assertEquals(413, IntakeDecision.httpStatus(decision).orElseThrow());
        assertEquals(0, calls.get());
        assertTrue(store.find("github", "delivery-1001").isEmpty());
        assertEquals(BodyLimit.DEFAULT_BYTES, 1024 * 1024);
    }

    @Test
    void shouldStoreNothingWhenKeyIsMissing() {
        var decision = intake.receive(command(body("{}"), null, github(body("{}"))), event -> new WebhookResult(RESULT));

        assertInstanceOf(IntakeDecision.MissingKey.class, decision);
        assertTrue(IntakeDecision.httpStatus(decision).isEmpty());
        assertTrue(store.find("github", "delivery-1001").isEmpty());
    }

    @Test
    void shouldRejectSignatureBeforeMissingKey() {
        var decision = intake.receive(command(body("{}"), "  ", "sha256=" + "ab".repeat(32)), event -> new WebhookResult(RESULT));

        assertEquals(IntakeDecision.Reason.SIGNATURE, assertInstanceOf(IntakeDecision.Rejected.class, decision).reason());
    }

    @Test
    void shouldDeleteRowWhenHandlerFails() {
        assertThrows(IllegalStateException.class, () -> intake.receive(command(body("{}"), "delivery-1001", github(body("{}"))), event -> {
            throw new IllegalStateException("boom");
        }));

        assertTrue(store.find("github", "delivery-1001").isEmpty());
    }

    @Test
    void shouldRunHandlerWhenLeaseHasExpired() {
        store.insertInProgress("github", "delivery-1001", "old", NOW.minusSeconds(1));
        var calls = new AtomicInteger();
        var payload = body("{}");

        var decision = intake.receive(command(payload, "delivery-1001", github(payload)), event -> {
            calls.incrementAndGet();
            return new WebhookResult(RESULT);
        });

        var completed = assertInstanceOf(IntakeDecision.Completed.class, decision);
        assertFalse(completed.replay());
        assertEquals(1, calls.get());
        assertEquals(Fingerprint.sha256Hex(payload), store.find("github", "delivery-1001").orElseThrow().fingerprint());
    }

    @Test
    void shouldKeepSuccessorLeaseWhenHandlerFailsAfterTakeover() {
        assertThrows(IllegalStateException.class, () -> intake.receive(
                command(body("{}"), "delivery-1001", github(body("{}"))),
                event -> {
                    var row = store.find("github", "delivery-1001").orElseThrow();
                    var successor = NOW.plus(Duration.ofMinutes(5));
                    assertTrue(store.takeover("github", "delivery-1001", "other", row.lockedUntil(), successor));
                    throw new IllegalStateException("boom");
                }));

        var kept = store.find("github", "delivery-1001").orElseThrow();
        assertEquals(IdempotencyRecord.Status.IN_PROGRESS, kept.status());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), kept.lockedUntil());
        assertEquals("other", kept.fingerprint());
    }

    @Test
    void shouldLeaveSuccessorLeaseWhenCompletionLosesTheRace() {
        assertThrows(IllegalStateException.class, () -> intake.receive(
                command(body("{}"), "delivery-1001", github(body("{}"))),
                event -> {
                    var row = store.find("github", "delivery-1001").orElseThrow();
                    assertTrue(store.takeover(
                            "github", "delivery-1001", "other", row.lockedUntil(), NOW.plus(Duration.ofMinutes(5))));
                    return new WebhookResult(RESULT);
                }));

        var kept = store.find("github", "delivery-1001").orElseThrow();
        assertEquals(IdempotencyRecord.Status.IN_PROGRESS, kept.status());
        assertEquals("other", kept.fingerprint());
        assertEquals(NOW.plus(Duration.ofMinutes(5)), kept.lockedUntil());
    }

    @Test
    void shouldVerifyRawBytesThatAreNotUtf8() {
        var payload = new byte[] {(byte) 0xff, 0x00};
        var decision = intake.receive(command(payload, "delivery-1001", github(payload)), event -> new WebhookResult(RESULT));

        assertInstanceOf(IntakeDecision.Completed.class, decision);
    }

    private static IntakeCommand command(byte[] payload, String key, String header) {
        return new IntakeCommand(
                "github",
                key,
                payload,
                BodyLimit.DEFAULT_BYTES,
                SignatureScheme.GITHUB,
                header,
                List.of(new SigningSecret(SECRET.getBytes(StandardCharsets.US_ASCII), null)),
                null,
                Duration.ofMinutes(1));
    }

    private static byte[] body(String json) {
        return json.getBytes(StandardCharsets.UTF_8);
    }

    private static String github(byte[] payload) {
        try {
            var mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(SECRET.getBytes(StandardCharsets.US_ASCII), "HmacSHA256"));
            return "sha256=" + java.util.HexFormat.of().formatHex(mac.doFinal(payload));
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }
}
