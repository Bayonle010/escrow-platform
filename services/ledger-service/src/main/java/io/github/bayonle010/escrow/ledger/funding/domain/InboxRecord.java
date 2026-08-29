package io.github.bayonle010.escrow.ledger.funding.domain;

import java.util.UUID;

public record InboxRecord(UUID aggregateId, String eventType) {
}
