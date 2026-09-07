package io.github.bayonle010.escrow.escrow.messaging.ledger;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.escrow.funding.domain.EscrowFundingSecuredEvent;
import io.github.bayonle010.escrow.escrow.funding.service.EscrowFundingService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class EscrowFundingSecuredKafkaConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(EscrowFundingSecuredKafkaConsumer.class);
    private static final String EVENT_TYPE = "EscrowFundingSecured";
    private static final Duration TIMESTAMP_PRECISION_TOLERANCE = Duration.ofNanos(1_000);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final EscrowFundingService fundingService;

    public EscrowFundingSecuredKafkaConsumer(
            ObjectMapper objectMapper,
            Validator validator,
            EscrowFundingService fundingService) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.fundingService = fundingService;
    }

    @KafkaListener(
            topics = "${escrow.messaging.topics.ledger-events:ledger.events.v1}",
            groupId = "${spring.kafka.consumer.group-id:escrow-funding-secured-v1}",
            autoStartup = "${escrow.messaging.consumer-enabled:true}"
    )
    public void consume(String eventJson) {
        LedgerEventEnvelope envelope = deserializeEnvelope(eventJson);
        requireEnvelopeMetadata(envelope);
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            LOGGER.atDebug()
                    .addKeyValue("eventId", envelope.eventId())
                    .addKeyValue("eventType", envelope.eventType())
                    .log("Ignoring Ledger event not consumed by Escrow");
            return;
        }

        EscrowFundingSecuredMessage payload = deserializePayload(envelope);
        validateConsistency(envelope, payload);
        validatePayload(payload);

        var result = fundingService.secure(toDomain(envelope, payload));
        LOGGER.atInfo()
                .addKeyValue("eventId", envelope.eventId())
                .addKeyValue("escrowId", payload.escrowId())
                .addKeyValue("journalId", payload.journalId())
                .addKeyValue("state", result.state())
                .addKeyValue("replayed", result.replayed())
                .log("Processed EscrowFundingSecured event from Kafka");
    }

    private LedgerEventEnvelope deserializeEnvelope(String eventJson) {
        try {
            return objectMapper.readValue(eventJson, LedgerEventEnvelope.class);
        } catch (JacksonException exception) {
            throw new InvalidLedgerEventException("Ledger event envelope is not valid JSON.", exception);
        }
    }

    private EscrowFundingSecuredMessage deserializePayload(LedgerEventEnvelope envelope) {
        if (envelope.payload() == null) {
            throw new InvalidLedgerEventException("Ledger event payload is required.");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), EscrowFundingSecuredMessage.class);
        } catch (JacksonException exception) {
            throw new InvalidLedgerEventException("EscrowFundingSecured payload is invalid.", exception);
        }
    }

    private void requireEnvelopeMetadata(LedgerEventEnvelope envelope) {
        if (envelope.eventId() == null
                || envelope.aggregateType() == null
                || envelope.aggregateId() == null
                || envelope.eventType() == null
                || envelope.occurredAt() == null
                || envelope.correlationId() == null
                || envelope.causationId() == null) {
            throw new InvalidLedgerEventException("Ledger event envelope metadata is incomplete.");
        }
        if (!"LedgerJournal".equals(envelope.aggregateType())) {
            throw new InvalidLedgerEventException("Ledger event aggregateType must be LedgerJournal.");
        }
    }

    private void validateConsistency(
            LedgerEventEnvelope envelope,
            EscrowFundingSecuredMessage payload) {
        if (!EVENT_TYPE.equals(payload.eventType())
                || envelope.eventVersion() != payload.eventVersion()
                || !timestampsMatch(envelope.occurredAt(), payload.occurredAt())
                || !envelope.correlationId().equals(payload.correlationId())
                || !envelope.causationId().equals(payload.causationId())
                || !envelope.aggregateId().equals(payload.journalId())) {
            throw new InvalidLedgerEventException(
                    "EscrowFundingSecured envelope metadata does not match its payload.");
        }
    }

    private boolean timestampsMatch(Instant envelopeTimestamp, Instant payloadTimestamp) {
        return payloadTimestamp != null
                && Duration.between(envelopeTimestamp, payloadTimestamp)
                        .abs()
                        .compareTo(TIMESTAMP_PRECISION_TOLERANCE) < 0;
    }

    private void validatePayload(EscrowFundingSecuredMessage payload) {
        Set<ConstraintViolation<EscrowFundingSecuredMessage>> violations = validator.validate(payload);
        if (!violations.isEmpty()) {
            ConstraintViolation<EscrowFundingSecuredMessage> violation = violations.iterator().next();
            throw new InvalidLedgerEventException(
                    "EscrowFundingSecured payload validation failed: "
                            + violation.getPropertyPath() + " " + violation.getMessage());
        }
    }

    private EscrowFundingSecuredEvent toDomain(
            LedgerEventEnvelope envelope,
            EscrowFundingSecuredMessage payload) {
        return new EscrowFundingSecuredEvent(
                envelope.eventId(),
                envelope.eventVersion(),
                envelope.occurredAt(),
                payload.journalId(),
                payload.paymentId(),
                payload.escrowId(),
                payload.amountMinor(),
                payload.currency(),
                envelope.correlationId(),
                envelope.causationId());
    }
}
