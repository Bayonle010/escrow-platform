package io.github.bayonle010.escrow.escrow.funding.repository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.bayonle010.escrow.escrow.funding.domain.InboxRecord;

@Repository
public class EscrowFundingInboxRepository {

    private final JdbcTemplate jdbcTemplate;

    public EscrowFundingInboxRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claimEvent(
            String consumerName,
            UUID eventId,
            UUID aggregateId,
            String eventType,
            Instant processedAt) {
        return jdbcTemplate.update(
                """
                INSERT INTO consumer_inbox (
                    consumer_name, event_id, aggregate_id, event_type, processed_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """,
                consumerName,
                eventId,
                aggregateId,
                eventType,
                databaseTimestamp(processedAt)) == 1;
    }

    public Optional<InboxRecord> findInbox(String consumerName, UUID eventId) {
        return jdbcTemplate.query(
                """
                SELECT aggregate_id, event_type
                FROM consumer_inbox
                WHERE consumer_name = ? AND event_id = ?
                """,
                (resultSet, rowNumber) -> new InboxRecord(
                        resultSet.getObject("aggregate_id", UUID.class),
                        resultSet.getString("event_type")),
                consumerName,
                eventId).stream().findFirst();
    }

    private static OffsetDateTime databaseTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
