package io.github.bayonle010.escrow.payment.funding.gateway;

import java.util.UUID;

import io.github.bayonle010.escrow.payment.funding.domain.EscrowFundingSnapshot;

public interface EscrowFundingSnapshotGateway {

    EscrowFundingSnapshot get(UUID escrowId, UUID correlationId);
}
