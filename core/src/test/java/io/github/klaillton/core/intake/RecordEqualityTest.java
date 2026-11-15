package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import org.junit.jupiter.api.Test;

class RecordEqualityTest {

    private static final Instant UNTIL = Instant.parse("2026-01-01T00:00:00Z");
    private static final byte[] BODY = {1, 2, 3};

    @Test
    void shouldMatchWebhookResultWhenTheBodyBytesMatch() {
        var input = new byte[] {1, 2, 3};
        var result = new WebhookResult(input);
        var copy = new WebhookResult(new byte[] {1, 2, 3});

        assertEquals(copy, result);
        assertEquals(Arrays.hashCode(result.body()), result.hashCode());
        assertEquals("WebhookResult[bodyLength=3]", result.toString());
        assertNotEquals(result, new WebhookResult(new byte[] {1, 2}));
        assertNotEquals(result, new WebhookResult(null));
        assertFalse(result.equals("WebhookResult"));
        assertBytesAreCopied(input, result::body);

        var empty = new WebhookResult(null);
        assertArrayEquals(new byte[0], empty.body());
        assertEquals("WebhookResult[bodyLength=0]", empty.toString());
        assertEquals(empty, new WebhookResult(new byte[0]));
    }

    @Test
    void shouldMatchWebhookEventWhenEveryFieldMatches() {
        var input = new byte[] {1, 2, 3};
        var event = new WebhookEvent("github", "delivery-1001", input, "fp");
        var copy = new WebhookEvent("github", "delivery-1001", new byte[] {1, 2, 3}, "fp");

        assertEquals(copy, event);
        assertEquals(
                Objects.hash(event.source(), event.key(), Arrays.hashCode(event.body()), event.fingerprint()),
                event.hashCode());
        assertEquals(
                "WebhookEvent[source=github, key=delivery-1001, bodyLength=3, fingerprint=fp]", event.toString());
        assertNotEquals(event, new WebhookEvent("stripe", "delivery-1001", BODY, "fp"));
        assertNotEquals(event, new WebhookEvent("github", "other", BODY, "fp"));
        assertNotEquals(event, new WebhookEvent("github", "delivery-1001", new byte[] {9}, "fp"));
        assertNotEquals(event, new WebhookEvent("github", "delivery-1001", BODY, "other"));
        assertFalse(event.equals(null));
        assertBytesAreCopied(input, event::body);
    }

    @Test
    void shouldMatchSigningSecretWhenTheKeyAndExpiryMatch() {
        var expires = UNTIL.plusSeconds(5);
        var input = new byte[] {1, 2, 3};
        var secret = new SigningSecret(input, expires);
        var copy = new SigningSecret(new byte[] {1, 2, 3}, expires);

        assertEquals(copy, secret);
        assertEquals(31 * Arrays.hashCode(secret.key()) + Objects.hashCode(secret.expiresAt()), secret.hashCode());
        assertEquals("SigningSecret[keyLength=3, expiresAt=" + expires + "]", secret.toString());
        assertNotEquals(secret, new SigningSecret(new byte[] {9}, expires));
        assertNotEquals(secret, new SigningSecret(BODY, null));
        assertNotEquals(secret, new SigningSecret(BODY, expires.plusSeconds(1)));
        assertFalse(secret.equals("secret"));
        assertTrue(secret.activeAt(expires));
        assertFalse(secret.activeAt(expires.plusNanos(1)));
        assertTrue(new SigningSecret(BODY, null).activeAt(expires));
        assertBytesAreCopied(input, secret::key);
    }

    @Test
    void shouldMatchIdempotencyRecordWhenEveryFieldMatches() {
        var record = new IdempotencyRecord(
                "github", "delivery-1001", "fp", IdempotencyRecord.Status.IN_PROGRESS, UNTIL, null);
        var copy = new IdempotencyRecord(
                "github", "delivery-1001", "fp", IdempotencyRecord.Status.IN_PROGRESS, UNTIL, null);

        assertEquals(copy, record);
        assertEquals(record.hashCode(), copy.hashCode());
        assertEquals(expectedRecordHash(record), record.hashCode());
        assertEquals(
                "IdempotencyRecord[source=github, key=delivery-1001, fingerprint=fp, status=IN_PROGRESS, lockedUntil="
                        + UNTIL
                        + ", responseLength=0]",
                record.toString());
        assertNull(record.responseBody());
        assertNotEquals(record, recordWith("stripe", "delivery-1001", "fp", IdempotencyRecord.Status.IN_PROGRESS, UNTIL, null));
        assertNotEquals(record, recordWith("github", "other", "fp", IdempotencyRecord.Status.IN_PROGRESS, UNTIL, null));
        assertNotEquals(record, recordWith("github", "delivery-1001", "other", IdempotencyRecord.Status.IN_PROGRESS, UNTIL, null));
        assertNotEquals(record, recordWith("github", "delivery-1001", "fp", IdempotencyRecord.Status.COMPLETED, UNTIL, null));
        assertNotEquals(record, recordWith("github", "delivery-1001", "fp", IdempotencyRecord.Status.IN_PROGRESS, UNTIL.plusSeconds(1), null));
        assertNotEquals(record, recordWith("github", "delivery-1001", "fp", IdempotencyRecord.Status.IN_PROGRESS, UNTIL, BODY));
        assertFalse(record.equals(null));

        var response = new byte[] {1, 2, 3};
        var stored = recordWith("github", "delivery-1001", "fp", IdempotencyRecord.Status.COMPLETED, UNTIL, response);
        assertEquals(
                "IdempotencyRecord[source=github, key=delivery-1001, fingerprint=fp, status=COMPLETED, lockedUntil="
                        + UNTIL
                        + ", responseLength=3]",
                stored.toString());
        assertBytesAreCopied(response, stored::responseBody);

        var taken = stored.takenOver("next", UNTIL.plusSeconds(5));
        assertEquals("next", taken.fingerprint());
        assertEquals(UNTIL.plusSeconds(5), taken.lockedUntil());
        assertEquals(IdempotencyRecord.Status.IN_PROGRESS, taken.status());
        assertNull(taken.responseBody());

        var completed = record.completed(BODY);
        assertArrayEquals(BODY, completed.responseBody());
        assertEquals(IdempotencyRecord.Status.COMPLETED, completed.status());
        assertEquals("fp", completed.fingerprint());
    }

    @Test
    void shouldMatchIntakeCommandWhenEveryFieldMatches() {
        var body = new byte[] {1, 2, 3};
        var secrets = new ArrayList<SigningSecret>();
        secrets.add(new SigningSecret(new byte[] {4}, null));
        var command = new IntakeCommand(
                "github",
                "delivery-1001",
                body,
                8,
                SignatureScheme.GITHUB,
                "sha256=ab",
                secrets,
                Duration.ofMinutes(5),
                Duration.ofMinutes(2));
        var copy = command("github", "delivery-1001", new byte[] {1, 2, 3}, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2));

        assertEquals(copy, command);
        assertEquals(expectedCommandHash(command), command.hashCode());
        assertEquals(
                "IntakeCommand[source=github, key=delivery-1001, bodyLength=3, bodyLimitBytes=8, scheme=GITHUB, lease=PT2M]",
                command.toString());
        assertNotEquals(command, command("stripe", "delivery-1001", BODY, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "other", BODY, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", null, BODY, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", new byte[] {9}, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 9, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 8, SignatureScheme.STRIPE, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 8, SignatureScheme.GITHUB, "sha256=ff", Duration.ofMinutes(5), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 8, SignatureScheme.GITHUB, null, Duration.ofMinutes(5), Duration.ofMinutes(2)));
        var otherSecret = List.of(new SigningSecret(new byte[] {9}, null));
        assertNotEquals(
                command,
                new IntakeCommand(
                        "github",
                        "delivery-1001",
                        BODY,
                        8,
                        SignatureScheme.GITHUB,
                        "sha256=ab",
                        otherSecret,
                        Duration.ofMinutes(5),
                        Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 8, SignatureScheme.GITHUB, "sha256=ab", null, Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(1), Duration.ofMinutes(2)));
        assertNotEquals(command, command("github", "delivery-1001", BODY, 8, SignatureScheme.GITHUB, "sha256=ab", Duration.ofMinutes(5), Duration.ofMinutes(3)));
        assertFalse(command.equals("command"));
        assertBytesAreCopied(body, command::body);
        secrets.add(new SigningSecret(new byte[] {7}, null));
        assertEquals(1, command.secrets().size());
        assertArrayEquals(new byte[] {4}, command.secrets().getFirst().key());
    }

    @Test
    void shouldRejectIntakeCommandWhenARequiredFieldIsInvalid() {
        assertThrows(IllegalArgumentException.class, () -> command("  ", "k", BODY, 1, SignatureScheme.GITHUB, null, null, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> command("github", "k", BODY, -1, SignatureScheme.GITHUB, null, null, Duration.ofMinutes(1)));
        assertThrows(IllegalArgumentException.class, () -> command("github", "k", BODY, 1, SignatureScheme.GITHUB, null, null, Duration.ZERO));
        assertThrows(IllegalArgumentException.class, () -> command("github", "k", BODY, 1, SignatureScheme.GITHUB, null, null, Duration.ofMinutes(-1)));
        assertThrows(NullPointerException.class, () -> command(null, "k", BODY, 1, SignatureScheme.GITHUB, null, null, Duration.ofMinutes(1)));
    }

    @Test
    void shouldMatchCompletedDecisionWhenTheBodyAndReplayFlagMatch() {
        var input = new byte[] {1, 2, 3};
        var completed = new IntakeDecision.Completed(input, false);
        var copy = new IntakeDecision.Completed(new byte[] {1, 2, 3}, false);

        assertEquals(copy, completed);
        assertEquals(31 * Arrays.hashCode(completed.body()) + Boolean.hashCode(completed.replay()), completed.hashCode());
        assertEquals("Completed[bodyLength=3, replay=false]", completed.toString());
        assertNotEquals(completed, new IntakeDecision.Completed(new byte[] {9}, false));
        assertNotEquals(completed, new IntakeDecision.Completed(BODY, true));
        assertFalse(completed.equals(new IntakeDecision.MissingKey()));
        assertBytesAreCopied(input, completed::body);

        var empty = new IntakeDecision.Completed(null, true);
        assertArrayEquals(new byte[0], empty.body());
        assertEquals("Completed[bodyLength=0, replay=true]", empty.toString());
        assertTrue(IntakeDecision.sameBody(BODY, new byte[] {1, 2, 3}));
        assertFalse(IntakeDecision.sameBody(BODY, new byte[] {1}));
        assertFalse(IntakeDecision.sameBody(null, BODY));
        assertTrue(IntakeDecision.sameBody(null, null));
    }

    private static IdempotencyRecord recordWith(
            String source, String key, String fingerprint, IdempotencyRecord.Status status, Instant lockedUntil, byte[] response) {
        return new IdempotencyRecord(source, key, fingerprint, status, lockedUntil, response);
    }

    private static int expectedRecordHash(IdempotencyRecord record) {
        return Objects.hash(
                record.source(),
                record.key(),
                record.fingerprint(),
                record.status(),
                record.lockedUntil(),
                Arrays.hashCode(record.responseBody()));
    }

    private static IntakeCommand command(
            String source,
            String key,
            byte[] body,
            int limit,
            SignatureScheme scheme,
            String header,
            Duration tolerance,
            Duration lease) {
        return new IntakeCommand(
                source,
                key,
                body,
                limit,
                scheme,
                header,
                List.of(new SigningSecret(new byte[] {4}, null)),
                tolerance,
                lease);
    }

    private static void assertBytesAreCopied(byte[] input, Supplier<byte[]> stored) {
        input[0] = 9;
        assertArrayEquals(new byte[] {1, 2, 3}, stored.get());
        var leaked = stored.get();
        leaked[0] = 8;
        assertArrayEquals(new byte[] {1, 2, 3}, stored.get());
    }

    private static int expectedCommandHash(IntakeCommand command) {
        return Objects.hash(
                command.source(),
                command.key(),
                Arrays.hashCode(command.body()),
                command.bodyLimitBytes(),
                command.scheme(),
                command.signatureHeader(),
                command.secrets(),
                command.timestampTolerance(),
                command.lease());
    }
}
