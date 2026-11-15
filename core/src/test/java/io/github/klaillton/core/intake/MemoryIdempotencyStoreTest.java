package io.github.klaillton.core.intake;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MemoryIdempotencyStoreTest {

    private static final Instant UNTIL = Instant.parse("2026-01-01T00:00:00Z");

    private final MemoryIdempotencyStore store = new MemoryIdempotencyStore();

    @Test
    void shouldRejectInsertWhenTheKeyAlreadyExists() {
        assertTrue(store.insertInProgress("github", "delivery-1001", "fp", UNTIL));

        assertFalse(store.insertInProgress("github", "delivery-1001", "other", UNTIL.plusSeconds(1)));

        assertEquals("fp", store.find("github", "delivery-1001").orElseThrow().fingerprint());
        assertEquals(UNTIL, store.find("github", "delivery-1001").orElseThrow().lockedUntil());
    }

    @Test
    void shouldRejectTakeoverWhenTheRowIsMissingCompletedOrTheLeaseDiffers() {
        var next = UNTIL.plusSeconds(30);
        assertFalse(store.takeover("github", "missing", "next", UNTIL, next));

        assertTrue(store.insertInProgress("github", "delivery-1001", "fp", UNTIL));
        assertFalse(store.takeover("github", "delivery-1001", "next", UNTIL.plusSeconds(1), next));
        assertEquals("fp", store.find("github", "delivery-1001").orElseThrow().fingerprint());

        assertTrue(store.complete("github", "delivery-1001", UNTIL, new byte[] {1}));
        assertFalse(store.takeover("github", "delivery-1001", "next", UNTIL, next));
        assertEquals(IdempotencyRecord.Status.COMPLETED, store.find("github", "delivery-1001").orElseThrow().status());
    }

    @Test
    void shouldReportWhetherDeleteRemovedTheInProgressRow() {
        assertFalse(store.deleteInProgress("github", "delivery-1001", UNTIL));

        assertTrue(store.insertInProgress("github", "delivery-1001", "fp", UNTIL));
        assertFalse(store.deleteInProgress("github", "delivery-1001", UNTIL.plusSeconds(1)));
        assertTrue(store.find("github", "delivery-1001").isPresent());

        assertTrue(store.deleteInProgress("github", "delivery-1001", UNTIL));
        assertTrue(store.find("github", "delivery-1001").isEmpty());
        assertFalse(store.deleteInProgress("github", "delivery-1001", UNTIL));
    }

    @Test
    void shouldRejectCompleteWhenThisAttemptNoLongerOwnsTheRow() {
        assertFalse(store.complete("github", "delivery-1001", UNTIL, new byte[] {1}));

        assertTrue(store.insertInProgress("github", "delivery-1001", "fp", UNTIL));
        assertFalse(store.complete("github", "delivery-1001", UNTIL.plusSeconds(1), new byte[] {1}));
        assertEquals(IdempotencyRecord.Status.IN_PROGRESS, store.find("github", "delivery-1001").orElseThrow().status());
    }
}
