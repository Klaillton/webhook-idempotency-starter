package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for one delivery. The Postgres adapter lives in the starter.
 * {@code lockedUntil} on insert and takeover is the lease end the caller calculated.
 */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String source, String key);

    /**
     * Insert the in-progress row. {@code createdAt} is the intake clock.
     * The adapter stores {@code expires_at} as that instant plus seven days.
     */
    boolean insertInProgress(String source, String key, String fingerprint, Instant lockedUntil, Instant createdAt);

    /**
     * Drop the stored body of a completed row. The row stays completed.
     * Returns false when the row is missing, not completed, or already has no body.
     */
    boolean clearStoredBody(String source, String key);

    /**
     * Replace an in-progress row only when its lease end is still {@code expectedLockedUntil}.
     * Returns false when another worker already changed the row.
     */
    boolean takeover(
            String source, String key, String fingerprint, Instant expectedLockedUntil, Instant newLockedUntil);

    /**
     * Mark the row completed only when it is still in progress at {@code expectedLockedUntil}.
     * Returns false when a takeover already replaced this attempt.
     */
    boolean complete(String source, String key, Instant expectedLockedUntil, byte[] responseBody);

    /**
     * Drop the in-progress row only when its lease end is still {@code expectedLockedUntil}.
     * A later takeover stays. Returns false when this attempt no longer owns the row.
     */
    boolean deleteInProgress(String source, String key, Instant expectedLockedUntil);
}
