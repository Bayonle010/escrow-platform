package io.github.bayonle010.escrow.ledger.messaging.outbox;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
public class LedgerOutboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerOutboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public List<LedgerOutboxEvent> lockNextBatch(Instant now, int batchSize) {
        return jdbcTemplate.query(
                """
                SELECT event_id, aggregate_id, aggregate_type, event_type, event_version,
                       partition_key, correlation_id, causation_id, payload, occurred_at, attempts
                FROM outbox_events
                WHERE status = 'PENDING'
                  AND next_attempt_at <= ?
                ORDER BY occurred_at, event_id
                LIMIT ?
                FOR UPDATE SKIP LOCKED
                """,
                (resultSet, rowNumber) -> mapEvent(resultSet),
                databaseTimestamp(now),
                batchSize);
    }

    public void markPublished(UUID eventId, Instant publishedAt) {
        requireSingleUpdate(jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET status = 'PUBLISHED', attempts = attempts + 1, published_at = ?
                WHERE event_id = ? AND status = 'PENDING'
                """,
                databaseTimestamp(publishedAt),
                eventId));
    }

    public void scheduleRetry(UUID eventId, Instant retryAt) {
        requireSingleUpdate(jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET attempts = attempts + 1, next_attempt_at = ?
                WHERE event_id = ? AND status = 'PENDING'
                """,
                databaseTimestamp(retryAt),
                eventId));
    }

    public void markFailed(UUID eventId) {
        requireSingleUpdate(jdbcTemplate.update(
                """
                UPDATE outbox_events
                SET status = 'FAILED', attempts = attempts + 1
                WHERE event_id = ? AND status = 'PENDING'
                """,
                eventId));
    }

    private LedgerOutboxEvent mapEvent(ResultSet resultSet) throws SQLException {
        return new LedgerOutboxEvent(
                resultSet.getObject("event_id", UUID.class),
                resultSet.getObject("aggregate_id", UUID.class),
                resultSet.getString("aggregate_type"),
                resultSet.getString("event_type"),
                resultSet.getInt("event_version"),
                resultSet.getObject("partition_key", UUID.class),
                resultSet.getObject("correlation_id", UUID.class),
                resultSet.getObject("causation_id", UUID.class),
                resultSet.getString("payload"),
                resultSet.getTimestamp("occurred_at").toInstant(),
                resultSet.getInt("attempts"));
    }

    private void requireSingleUpdate(int updatedRows) {
        if (updatedRows != 1) {
            throw new IllegalStateException("Expected exactly one Ledger outbox row to be updated.");
        }
    }

    private static OffsetDateTime databaseTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
