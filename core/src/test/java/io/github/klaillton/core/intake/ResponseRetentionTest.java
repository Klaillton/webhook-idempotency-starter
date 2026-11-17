package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ResponseRetentionTest {

    private static final Instant CREATED = Instant.parse("2026-06-01T12:00:00Z");

    @Test
    void shouldKeepABodyAtThe64KbLimitAndDropALargerOne() {
        var exact = new byte[ResponseRetention.MAX_STORED_BODY_BYTES];
        var larger = new byte[ResponseRetention.MAX_STORED_BODY_BYTES + 1];

        assertArrayEquals(exact, ResponseRetention.bodyToStore(exact));
        assertNull(ResponseRetention.bodyToStore(larger));
        assertNull(ResponseRetention.bodyToStore(null));
    }

    @Test
    void shouldExpireTheRecordSevenDaysAfterItWasCreated() {
        assertEquals(CREATED.plus(ResponseRetention.RECORD), ResponseRetention.recordExpiresAt(CREATED));
    }

    @Test
    void shouldReplayAnEmptyBodyWhenTheStoredBodyIsMissingOr24HoursOld() {
        var stored = new byte[] {1, 2, 3};
        var fresh = CREATED.plus(ResponseRetention.BODY).minusNanos(1);
        var expired = CREATED.plus(ResponseRetention.BODY);

        assertEquals(0, ResponseRetention.replayBody(CREATED, null, fresh).length);
        assertArrayEquals(stored, ResponseRetention.replayBody(CREATED, stored, fresh));
        assertEquals(0, ResponseRetention.replayBody(CREATED, stored, expired).length);
        assertFalse(ResponseRetention.shouldClearStoredBody(CREATED, null, expired));
        assertFalse(ResponseRetention.shouldClearStoredBody(CREATED, stored, fresh));
        assertTrue(ResponseRetention.shouldClearStoredBody(CREATED, stored, expired));
    }
}
