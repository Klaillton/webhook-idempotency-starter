package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.Optional;

/**
 * Persistence port for one delivery. The Postgres adapter is not part of this slice.
 * {@code lockedUntil} on insert and takeover is the lease end the caller calculated.
 */
public interface IdempotencyStore {

    Optional<IdempotencyRecord> find(String source, String key);

    boolean insertInProgress(String source, String key, String fingerprint, Instant lockedUntil);

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
