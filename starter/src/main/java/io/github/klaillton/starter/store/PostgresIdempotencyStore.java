package io.github.klaillton.starter.store;

import io.github.klaillton.core.intake.IdempotencyRecord;
import io.github.klaillton.core.intake.IdempotencyStore;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import org.springframework.jdbc.core.JdbcTemplate;

/** JDBC store. Callers own the transaction, so a handler failure rolls the row back. */
public final class PostgresIdempotencyStore implements IdempotencyStore {

    private static final String INSERT = """
            INSERT INTO idempotency_record
                (source, key, fingerprint, status, locked_until, created_at)
            VALUES (?, ?, ?, 'IN_PROGRESS', ?, CURRENT_TIMESTAMP)
            ON CONFLICT (source, key) DO NOTHING
            """;

    private static final String FIND = """
            SELECT source, key, fingerprint, status, locked_until, response_body
            FROM idempotency_record
            WHERE source = ? AND key = ?
            """;

    private static final String TAKEOVER = """
            UPDATE idempotency_record
            SET fingerprint = ?, status = 'IN_PROGRESS', locked_until = ?,
                response_code = NULL, response_body = NULL
            WHERE source = ? AND key = ? AND status = 'IN_PROGRESS' AND locked_until = ?
            """;

    private static final String COMPLETE = """
            UPDATE idempotency_record
            SET status = 'COMPLETED', response_code = 200, response_body = ?
            WHERE source = ? AND key = ? AND status = 'IN_PROGRESS' AND locked_until = ?
            """;

    private static final String DELETE = """
            DELETE FROM idempotency_record
            WHERE source = ? AND key = ? AND status = 'IN_PROGRESS' AND locked_until = ?
            """;

    private final JdbcTemplate jdbc;

    public PostgresIdempotencyStore(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public Optional<IdempotencyRecord> find(String source, String key) {
        var rows = jdbc.query(FIND, this::map, source, key);
        if (rows.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(rows.getFirst());
    }

    @Override
    public boolean insertInProgress(String source, String key, String fingerprint, Instant lockedUntil) {
        return jdbc.update(INSERT, source, key, fingerprint, utc(lockedUntil)) == 1;
    }

    @Override
    public boolean takeover(
            String source, String key, String fingerprint, Instant expectedLockedUntil, Instant newLockedUntil) {
        return jdbc.update(TAKEOVER, fingerprint, utc(newLockedUntil), source, key, utc(expectedLockedUntil)) == 1;
    }

    @Override
    public boolean complete(String source, String key, Instant expectedLockedUntil, byte[] responseBody) {
        return jdbc.update(COMPLETE, responseBody, source, key, utc(expectedLockedUntil)) == 1;
    }

    @Override
    public boolean deleteInProgress(String source, String key, Instant expectedLockedUntil) {
        return jdbc.update(DELETE, source, key, utc(expectedLockedUntil)) == 1;
    }

    private IdempotencyRecord map(ResultSet rs, int row) throws SQLException {
        return new IdempotencyRecord(
                rs.getString("source"),
                rs.getString("key"),
                rs.getString("fingerprint"),
                status(rs.getString("status")),
                utcInstant(rs.getObject("locked_until", OffsetDateTime.class)),
                rs.getBytes("response_body"));
    }

    private static IdempotencyRecord.Status status(String value) {
        return switch (value) {
            case "IN_PROGRESS" -> IdempotencyRecord.Status.IN_PROGRESS;
            case "COMPLETED" -> IdempotencyRecord.Status.COMPLETED;
            default -> throw new IllegalStateException("unknown idempotency status");
        };
    }

    private static OffsetDateTime utc(Instant instant) {
        return instant.truncatedTo(ChronoUnit.MICROS).atOffset(ZoneOffset.UTC);
    }

    private static Instant utcInstant(OffsetDateTime value) {
        return value.toInstant().truncatedTo(ChronoUnit.MICROS);
    }
}
