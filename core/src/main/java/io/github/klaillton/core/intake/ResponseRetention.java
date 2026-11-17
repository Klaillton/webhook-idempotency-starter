package io.github.klaillton.core.intake;

import java.time.Duration;
import java.time.Instant;

/**
 * ADR-0004 retention. A later cleanup job may delete rows after {@code expires_at}.
 * This type does not delete them.
 */
public final class ResponseRetention {

    public static final int MAX_STORED_BODY_BYTES = 64 * 1024;
    public static final Duration BODY = Duration.ofHours(24);
    public static final Duration RECORD = Duration.ofDays(7);

    private ResponseRetention() {}

    public static Instant recordExpiresAt(Instant createdAt) {
        return createdAt.plus(RECORD);
    }

    /**
     * Bytes written on the row. A result above 64 KB is stored as null and the row still completes.
     */
    public static byte[] bodyToStore(byte[] produced) {
        if (produced == null || produced.length > MAX_STORED_BODY_BYTES) {
            return null;
        }
        return produced;
    }

    /** Replay bytes. A missing or expired stored body becomes an empty array. The status stays completed. */
    public static byte[] replayBody(Instant createdAt, byte[] stored, Instant now) {
        if (stored == null || !now.isBefore(createdAt.plus(BODY))) {
            return new byte[0];
        }
        return stored;
    }

    public static boolean shouldClearStoredBody(Instant createdAt, byte[] stored, Instant now) {
        return stored != null && !now.isBefore(createdAt.plus(BODY));
    }
}
