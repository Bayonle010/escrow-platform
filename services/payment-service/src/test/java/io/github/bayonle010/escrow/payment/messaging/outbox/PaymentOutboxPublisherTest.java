package io.github.bayonle010.escrow.payment.messaging.outbox;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.payment.funding.entity.OutboxStatus;
import io.github.bayonle010.escrow.payment.funding.repository.OutboxEventRepository;
import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class PaymentOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final String TOPIC = "payment.events.v1";

    @Mock
    private OutboxEventRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private PaymentOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new PaymentOutboxPublisher(
                repository,
                kafkaTemplate,
                new OutboxEventSerializer(JsonMapper.builder().build()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                TOPIC,
                10,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofMinutes(5));
    }

    @Test
    void marksAnEventPublishedOnlyAfterKafkaAcknowledgesIt() {
        OutboxEventEntity event = event(validPayload());
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(event));
        SendResult<String, String> result = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(result));

        publisher.publishDueEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PUBLISHED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getPublishedAt()).isEqualTo(NOW);
        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(TOPIC),
                org.mockito.ArgumentMatchers.eq("019c0000-0000-7000-8000-000000000020"),
                anyString());
    }

    @Test
    void keepsATransientKafkaFailurePendingWithExponentialBackoff() {
        OutboxEventEntity event = event(validPayload());
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        publisher.publishDueEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.PENDING);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(1));
        assertThat(event.getPublishedAt()).isNull();
    }

    @Test
    void marksMalformedStoredEventsFailedBecauseRetryCannotRepairThem() {
        OutboxEventEntity event = event("{\"amountMinor\":100000}");
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(event));

        publisher.publishDueEvents();

        assertThat(event.getStatus()).isEqualTo(OutboxStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private OutboxEventEntity event(String payload) {
        return OutboxEventEntity.builder()
                .eventId(UUID.fromString("019c0000-0000-7000-8000-000000000040"))
                .aggregateId(UUID.fromString("019c0000-0000-7000-8000-000000000030"))
                .aggregateType("Payment")
                .eventType("PaymentSucceeded")
                .eventVersion(1)
                .correlationId(UUID.fromString("019c0000-0000-7000-8000-000000000010"))
                .payload(payload)
                .occurredAt(NOW)
                .status(OutboxStatus.PENDING)
                .attempts(0)
                .nextAttemptAt(NOW)
                .build();
    }

    private String validPayload() {
        return "{\"escrowId\":\"019c0000-0000-7000-8000-000000000020\"}";
    }
}
