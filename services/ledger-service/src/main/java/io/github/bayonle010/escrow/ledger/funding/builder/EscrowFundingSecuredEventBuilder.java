package io.github.bayonle010.escrow.ledger.funding.builder;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.ledger.funding.domain.OutboxEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PostedFunding;
import io.github.bayonle010.escrow.ledger.funding.event.EscrowFundingSecuredPayload;
import io.github.bayonle010.escrow.ledger.shared.UuidV7Generator;
import tools.jackson.databind.ObjectMapper;

@Component
public class EscrowFundingSecuredEventBuilder {

    private static final String EVENT_TYPE = "EscrowFundingSecured";
    private static final int EVENT_VERSION = 1;

    private final UuidV7Generator uuidGenerator;
    private final ObjectMapper objectMapper;

    public EscrowFundingSecuredEventBuilder(
            UuidV7Generator uuidGenerator,
            ObjectMapper objectMapper) {
        this.uuidGenerator = uuidGenerator;
        this.objectMapper = objectMapper;
    }

    public OutboxEvent build(
            PostedFunding funding,
            PaymentSucceededEvent cause,
            Instant occurredAt) {
        Instant eventTime = occurredAt.truncatedTo(ChronoUnit.MICROS);
        var payload = new EscrowFundingSecuredPayload(
                EVENT_TYPE,
                EVENT_VERSION,
                eventTime,
                funding.journalId(),
                funding.paymentId(),
                funding.escrowId(),
                funding.amountMinor(),
                funding.currency(),
                cause.correlationId(),
                cause.eventId());
        return new OutboxEvent(
                uuidGenerator.generate(),
                funding.journalId(),
                "LedgerJournal",
                EVENT_TYPE,
                EVENT_VERSION,
                funding.escrowId(),
                cause.correlationId(),
                cause.eventId(),
                objectMapper.valueToTree(payload),
                eventTime);
    }
}
