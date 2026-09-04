package io.github.bayonle010.escrow.ledger.messaging.outbox;

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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;

import tools.jackson.databind.json.JsonMapper;

@ExtendWith(MockitoExtension.class)
class LedgerOutboxPublisherTest {

    private static final Instant NOW = Instant.parse("2026-08-28T00:00:00Z");
    private static final String TOPIC = "ledger.events.v1";
    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000060");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");

    @Mock
    private LedgerOutboxRepository repository;

    @Mock
    private KafkaTemplate<String, String> kafkaTemplate;

    private LedgerOutboxPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new LedgerOutboxPublisher(
                repository,
                kafkaTemplate,
                new LedgerOutboxEventSerializer(JsonMapper.builder().findAndAddModules().build()),
                Clock.fixed(NOW, ZoneOffset.UTC),
                TOPIC,
                10,
                Duration.ofSeconds(2),
                Duration.ofSeconds(1),
                Duration.ofMinutes(5));
    }

    @Test
    void marksAnEventPublishedOnlyAfterKafkaAcknowledgesIt() {
        LedgerOutboxEvent event = event(validPayload(), 0);
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(event));
        SendResult<String, String> result = mock(SendResult.class);
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.completedFuture(result));

        publisher.publishDueEvents();

        verify(kafkaTemplate).send(
                org.mockito.ArgumentMatchers.eq(TOPIC),
                org.mockito.ArgumentMatchers.eq(ESCROW_ID.toString()),
                anyString());
        verify(repository).markPublished(EVENT_ID, NOW);
        verify(repository, never()).scheduleRetry(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void keepsATransientKafkaFailurePendingWithExponentialBackoff() {
        LedgerOutboxEvent event = event(validPayload(), 2);
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(event));
        when(kafkaTemplate.send(anyString(), anyString(), anyString()))
                .thenReturn(CompletableFuture.failedFuture(new IllegalStateException("broker unavailable")));

        publisher.publishDueEvents();

        verify(repository).scheduleRetry(EVENT_ID, NOW.plusSeconds(4));
        verify(repository, never()).markPublished(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void marksMalformedStoredEventsFailedBecauseRetryCannotRepairThem() {
        LedgerOutboxEvent event = event("{\"amountMinor\":100000}", 0);
        when(repository.lockNextBatch(NOW, 10)).thenReturn(List.of(event));

        publisher.publishDueEvents();

        verify(repository).markFailed(EVENT_ID);
        verify(kafkaTemplate, never()).send(anyString(), anyString(), anyString());
    }

    private LedgerOutboxEvent event(String payload, int attempts) {
        return new LedgerOutboxEvent(
                EVENT_ID,
                UUID.fromString("019c0000-0000-7000-8000-000000000050"),
                "LedgerJournal",
                "EscrowFundingSecured",
                1,
                ESCROW_ID,
                UUID.fromString("019c0000-0000-7000-8000-000000000010"),
                UUID.fromString("019c0000-0000-7000-8000-000000000040"),
                payload,
                NOW,
                attempts);
    }

    private String validPayload() {
        return "{\"escrowId\":\"" + ESCROW_ID + "\"}";
    }
}
