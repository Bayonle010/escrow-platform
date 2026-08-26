package io.github.bayonle010.escrow.payment.funding.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;

public interface OutboxEventRepository extends JpaRepository<OutboxEventEntity, UUID> {
}
