package io.github.bayonle010.escrow.payment.funding.repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {

    @Query(value = """
            SELECT *
            FROM outbox_events
            WHERE status = 'PENDING'
              AND next_attempt_at <= :now
            ORDER BY occurred_at, event_id
            LIMIT :batchSize
            FOR UPDATE SKIP LOCKED
            """, nativeQuery = true)
    List<OutboxEventEntity> lockNextBatch(
            @Param("now") Instant now,
            @Param("batchSize") int batchSize);
}
