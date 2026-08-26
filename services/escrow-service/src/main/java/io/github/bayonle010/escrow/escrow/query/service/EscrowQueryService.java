package io.github.bayonle010.escrow.escrow.query.service;

import java.util.UUID;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.github.bayonle010.escrow.escrow.creation.entity.EscrowEntity;
import io.github.bayonle010.escrow.escrow.creation.repository.EscrowRepository;
import io.github.bayonle010.escrow.escrow.query.domain.EscrowDetails;
import io.github.bayonle010.escrow.escrow.shared.exception.EscrowNotFoundException;

@Service
public class EscrowQueryService {

    private final EscrowRepository escrowRepository;

    public EscrowQueryService(EscrowRepository escrowRepository) {
        this.escrowRepository = escrowRepository;
    }

    @Transactional(readOnly = true)
    public EscrowDetails get(UUID escrowId) {
        EscrowEntity escrow = escrowRepository.findById(escrowId)
                .orElseThrow(() -> new EscrowNotFoundException(escrowId));
        return new EscrowDetails(
                escrow.getEscrowId(),
                escrow.getBuyerId(),
                escrow.getSellerId(),
                escrow.getAmountMinor(),
                escrow.getCurrency(),
                escrow.getCurrentTermsVersion(),
                escrow.getState(),
                escrow.getDeliveryDeadline(),
                escrow.getCreatedAt(),
                escrow.getUpdatedAt());
    }
}
