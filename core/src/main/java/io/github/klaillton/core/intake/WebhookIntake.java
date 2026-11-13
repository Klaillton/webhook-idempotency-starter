package io.github.klaillton.core.intake;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Objects;

/**
 * Body limit, then signature, then the idempotency row.
 * Checks stop before the store when the body or the signature fails.
 */
public final class WebhookIntake {

    private static final int SETTLE_ATTEMPTS = 3;

    private final IdempotencyStore store;
    private final Clock clock;

    public WebhookIntake(IdempotencyStore store, Clock clock) {
        this.store = Objects.requireNonNull(store, "store");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public IntakeDecision receive(IntakeCommand command, WebhookHandler handler) {
        Objects.requireNonNull(command, "command");
        Objects.requireNonNull(handler, "handler");
        if (BodyLimit.exceeds(command.body(), command.bodyLimitBytes())) {
            return new IntakeDecision.Rejected(IntakeDecision.Reason.BODY_TOO_LARGE);
        }
        if (!signatureOk(command)) {
            return new IntakeDecision.Rejected(IntakeDecision.Reason.SIGNATURE);
        }
        if (command.key() == null || command.key().isBlank()) {
            return new IntakeDecision.MissingKey();
        }
        var fingerprint = Fingerprint.sha256Hex(command.body());
        for (var attempt = 0; attempt < SETTLE_ATTEMPTS; attempt++) {
            var decision = settle(command, handler, fingerprint);
            if (decision != null) {
                return decision;
            }
        }
        throw new IllegalStateException("idempotency store did not settle");
    }

    private boolean signatureOk(IntakeCommand command) {
        var now = clock.instant();
        return switch (command.scheme()) {
            case GITHUB -> GithubSignature.verifies(command.body(), command.signatureHeader(), command.secrets(), now);
            case STRIPE ->
                StripeSignature.verifies(
                        command.body(),
                        command.signatureHeader(),
                        command.secrets(),
                        command.timestampTolerance(),
                        now);
        };
    }

    private IntakeDecision settle(IntakeCommand command, WebhookHandler handler, String fingerprint) {
        var existing = store.find(command.source(), command.key());
        if (existing.isEmpty()) {
            var lockedUntil = clock.instant().plus(command.lease());
            if (!store.insertInProgress(command.source(), command.key(), fingerprint, lockedUntil)) {
                return null;
            }
            return runHandler(command, handler, fingerprint, lockedUntil);
        }
        return decideExisting(command, handler, fingerprint, existing.get());
    }

    private IntakeDecision decideExisting(
            IntakeCommand command, WebhookHandler handler, String fingerprint, IdempotencyRecord record) {
        if (record.status() == IdempotencyRecord.Status.COMPLETED) {
            if (record.fingerprint().equals(fingerprint)) {
                return new IntakeDecision.Completed(record.responseBody(), true);
            }
            return new IntakeDecision.Rejected(IntakeDecision.Reason.FINGERPRINT);
        }
        if (clock.instant().isBefore(record.lockedUntil())) {
            return new IntakeDecision.InProgress(wholeSecondsUntil(clock.instant(), record.lockedUntil()));
        }
        var newLock = clock.instant().plus(command.lease());
        if (!store.takeover(command.source(), command.key(), fingerprint, record.lockedUntil(), newLock)) {
            return null;
        }
        return runHandler(command, handler, fingerprint, newLock);
    }

    private IntakeDecision runHandler(
            IntakeCommand command, WebhookHandler handler, String fingerprint, Instant lockedUntil) {
        byte[] produced;
        try {
            var event = new WebhookEvent(command.source(), command.key(), command.body(), fingerprint);
            produced = Objects.requireNonNull(handler.handle(event), "handler result").body();
        } catch (RuntimeException ex) {
            store.deleteInProgress(command.source(), command.key(), lockedUntil);
            throw ex;
        }
        if (!store.complete(command.source(), command.key(), lockedUntil, produced)) {
            throw new IllegalStateException("in-progress delivery disappeared before completion");
        }
        return new IntakeDecision.Completed(produced, false);
    }

    /**
     * Partial seconds round up so the caller waits until the lease has ended.
     * An exact whole number of seconds is unchanged.
     */
    static long wholeSecondsUntil(Instant now, Instant lockedUntil) {
        var remaining = Duration.between(now, lockedUntil);
        if (remaining.isZero() || remaining.isNegative()) {
            return 0;
        }
        var seconds = remaining.toSeconds();
        if (remaining.getNano() > 0) {
            seconds++;
        }
        return seconds;
    }
}
