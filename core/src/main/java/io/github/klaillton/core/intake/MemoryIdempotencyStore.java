package io.github.klaillton.core.intake;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/** In-memory store for the domain rules. Not the production Postgres store. */
public final class MemoryIdempotencyStore implements IdempotencyStore {

    private final ConcurrentHashMap<Key, IdempotencyRecord> rows = new ConcurrentHashMap<>();

    @Override
    public Optional<IdempotencyRecord> find(String source, String key) {
        synchronized (rows) {
            return Optional.ofNullable(rows.get(new Key(source, key)));
        }
    }

    @Override
    public boolean insertInProgress(String source, String key, String fingerprint, Instant lockedUntil) {
        synchronized (rows) {
            var id = new Key(source, key);
            if (rows.containsKey(id)) {
                return false;
            }
            rows.put(
                    id,
                    new IdempotencyRecord(source, key, fingerprint, IdempotencyRecord.Status.IN_PROGRESS, lockedUntil, null));
            return true;
        }
    }

    @Override
    public boolean takeover(
            String source, String key, String fingerprint, Instant expectedLockedUntil, Instant newLockedUntil) {
        synchronized (rows) {
            var id = new Key(source, key);
            var current = rows.get(id);
            if (current == null || current.status() != IdempotencyRecord.Status.IN_PROGRESS) {
                return false;
            }
            if (!current.lockedUntil().equals(expectedLockedUntil)) {
                return false;
            }
            rows.put(id, current.takenOver(fingerprint, newLockedUntil));
            return true;
        }
    }

    @Override
    public boolean complete(String source, String key, Instant expectedLockedUntil, byte[] responseBody) {
        synchronized (rows) {
            var current = owned(source, key, expectedLockedUntil);
            if (current == null) {
                return false;
            }
            rows.put(new Key(source, key), current.completed(responseBody));
            return true;
        }
    }

    @Override
    public boolean deleteInProgress(String source, String key, Instant expectedLockedUntil) {
        synchronized (rows) {
            if (owned(source, key, expectedLockedUntil) == null) {
                return false;
            }
            rows.remove(new Key(source, key));
            return true;
        }
    }

    private IdempotencyRecord owned(String source, String key, Instant expectedLockedUntil) {
        var current = rows.get(new Key(source, key));
        if (current == null || current.status() != IdempotencyRecord.Status.IN_PROGRESS) {
            return null;
        }
        if (!current.lockedUntil().equals(expectedLockedUntil)) {
            return null;
        }
        return current;
    }

    private record Key(String source, String key) {}
}
