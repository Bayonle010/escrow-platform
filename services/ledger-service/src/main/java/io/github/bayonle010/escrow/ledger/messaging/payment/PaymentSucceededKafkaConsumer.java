package io.github.bayonle010.escrow.ledger.messaging.payment;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.ledger.funding.dto.PaymentSucceededEventRequest;
import io.github.bayonle010.escrow.ledger.funding.service.LedgerFundingService;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

@Component
public class PaymentSucceededKafkaConsumer {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentSucceededKafkaConsumer.class);
    private static final String EVENT_TYPE = "PaymentSucceeded";
    private static final String SUCCEEDED_STATUS = "SUCCEEDED";
    private static final Duration TIMESTAMP_PRECISION_TOLERANCE = Duration.ofNanos(1_000);

    private final ObjectMapper objectMapper;
    private final Validator validator;
    private final LedgerFundingService fundingService;

    public PaymentSucceededKafkaConsumer(
            ObjectMapper objectMapper,
            Validator validator,
            LedgerFundingService fundingService) {
        this.objectMapper = objectMapper;
        this.validator = validator;
        this.fundingService = fundingService;
    }

    @KafkaListener(
            topics = "${ledger.messaging.topics.payment-events:payment.events.v1}",
            groupId = "${spring.kafka.consumer.group-id:ledger-payment-succeeded-v1}",
            autoStartup = "${ledger.messaging.consumer-enabled:true}"
    )
    public void consume(String eventJson) {
        PaymentEventEnvelope envelope = deserializeEnvelope(eventJson);
        requireEnvelopeMetadata(envelope);
        if (!EVENT_TYPE.equals(envelope.eventType())) {
            LOGGER.atDebug()
                    .addKeyValue("eventId", envelope.eventId())
                    .addKeyValue("eventType", envelope.eventType())
                    .log("Ignoring payment event not consumed by Ledger");
            return;
        }

        PaymentSucceededMessage payload = deserializePayload(envelope);
        validateConsistency(envelope, payload);
        PaymentSucceededEventRequest request = toRequest(envelope, payload);
        validateRequest(request);

        var result = fundingService.secure(request.toDomain());
        LOGGER.atInfo()
                .addKeyValue("eventId", envelope.eventId())
                .addKeyValue("paymentId", payload.paymentId())
                .addKeyValue("escrowId", payload.escrowId())
                .addKeyValue("journalId", result.journalId())
                .addKeyValue("replayed", result.replayed())
                .log("Processed PaymentSucceeded event from Kafka");
    }

    private PaymentEventEnvelope deserializeEnvelope(String eventJson) {
        try {
            return objectMapper.readValue(eventJson, PaymentEventEnvelope.class);
        } catch (JacksonException exception) {
            throw new InvalidPaymentEventException("Payment event envelope is not valid JSON.", exception);
        }
    }

    private PaymentSucceededMessage deserializePayload(PaymentEventEnvelope envelope) {
        if (envelope.payload() == null) {
            throw new InvalidPaymentEventException("Payment event payload is required.");
        }
        try {
            return objectMapper.treeToValue(envelope.payload(), PaymentSucceededMessage.class);
        } catch (JacksonException exception) {
            throw new InvalidPaymentEventException("PaymentSucceeded payload is invalid.", exception);
        }
    }

    private void requireEnvelopeMetadata(PaymentEventEnvelope envelope) {
        if (envelope.eventId() == null
                || envelope.aggregateType() == null
                || envelope.aggregateId() == null
                || envelope.eventType() == null
                || envelope.occurredAt() == null
                || envelope.correlationId() == null) {
            throw new InvalidPaymentEventException("Payment event envelope metadata is incomplete.");
        }
        if (!"Payment".equals(envelope.aggregateType())) {
            throw new InvalidPaymentEventException("Payment event aggregateType must be Payment.");
        }
    }

    private void validateConsistency(
            PaymentEventEnvelope envelope,
            PaymentSucceededMessage payload) {
        if (!EVENT_TYPE.equals(payload.eventType())
                || envelope.eventVersion() != payload.eventVersion()
                || !timestampsMatch(envelope.occurredAt(), payload.occurredAt())
                || !envelope.correlationId().equals(payload.correlationId())
                || !envelope.aggregateId().equals(payload.paymentId())) {
            throw new InvalidPaymentEventException(
                    "PaymentSucceeded envelope metadata does not match its payload.");
        }
        if (!SUCCEEDED_STATUS.equals(payload.status())) {
            throw new InvalidPaymentEventException("PaymentSucceeded payload status must be SUCCEEDED.");
        }
    }

    private boolean timestampsMatch(Instant envelopeTimestamp, Instant payloadTimestamp) {
        return payloadTimestamp != null
                && Duration.between(envelopeTimestamp, payloadTimestamp)
                        .abs()
                        .compareTo(TIMESTAMP_PRECISION_TOLERANCE) < 0;
    }

    private PaymentSucceededEventRequest toRequest(
            PaymentEventEnvelope envelope,
            PaymentSucceededMessage payload) {
        return new PaymentSucceededEventRequest(
                envelope.eventId(),
                envelope.eventVersion(),
                envelope.occurredAt(),
                payload.paymentId(),
                payload.escrowId(),
                payload.payerId(),
                payload.amountMinor(),
                payload.currency(),
                payload.provider(),
                payload.providerReference(),
                payload.aggregateVersion(),
                envelope.correlationId());
    }

    private void validateRequest(PaymentSucceededEventRequest request) {
        Set<ConstraintViolation<PaymentSucceededEventRequest>> violations = validator.validate(request);
        if (!violations.isEmpty()) {
            ConstraintViolation<PaymentSucceededEventRequest> violation = violations.iterator().next();
            String firstViolation = violation.getPropertyPath() + " " + violation.getMessage();
            throw new InvalidPaymentEventException(
                    "PaymentSucceeded payload validation failed: " + firstViolation);
        }
    }
}
