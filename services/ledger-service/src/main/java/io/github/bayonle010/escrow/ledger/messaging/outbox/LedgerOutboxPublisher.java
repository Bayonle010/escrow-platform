package io.github.bayonle010.escrow.ledger.messaging.outbox;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import tools.jackson.core.JacksonException;

@Component
@ConditionalOnProperty(
        name = "ledger.outbox.publisher.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class LedgerOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(LedgerOutboxPublisher.class);

    private final LedgerOutboxRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final LedgerOutboxEventSerializer serializer;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final Duration publishTimeout;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;

    public LedgerOutboxPublisher(
            LedgerOutboxRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            LedgerOutboxEventSerializer serializer,
            Clock clock,
            @Value("${ledger.messaging.topics.events:ledger.events.v1}") String topic,
            @Value("${ledger.outbox.publisher.batch-size:100}") int batchSize,
            @Value("${ledger.outbox.publisher.publish-timeout:5s}") Duration publishTimeout,
            @Value("${ledger.outbox.publisher.initial-retry-delay:1s}") Duration initialRetryDelay,
            @Value("${ledger.outbox.publisher.max-retry-delay:5m}") Duration maxRetryDelay) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
        this.serializer = serializer;
        this.clock = clock;
        this.topic = topic;
        this.batchSize = batchSize;
        this.publishTimeout = publishTimeout;
        this.initialRetryDelay = initialRetryDelay;
        this.maxRetryDelay = maxRetryDelay;
    }

    @Scheduled(fixedDelayString = "${ledger.outbox.publisher.poll-interval:500ms}")
    @Transactional
    public void publishDueEvents() {
        Instant batchStartedAt = clock.instant();
        List<LedgerOutboxEvent> events = repository.lockNextBatch(batchStartedAt, batchSize);
        for (LedgerOutboxEvent event : events) {
            publish(event);
        }
    }

    private void publish(LedgerOutboxEvent event) {
        SerializedOutboxEvent serialized;
        try {
            serialized = serializer.serialize(event);
        } catch (JacksonException | IllegalArgumentException exception) {
            repository.markFailed(event.eventId());
            LOGGER.atError()
                    .addKeyValue("eventId", event.eventId())
                    .addKeyValue("eventType", event.eventType())
                    .setCause(exception)
                    .log("Ledger outbox event cannot be serialized and will not be retried");
            return;
        }

        try {
            kafkaTemplate.send(topic, serialized.partitionKey(), serialized.value())
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            Instant publishedAt = clock.instant();
            repository.markPublished(event.eventId(), publishedAt);
            LOGGER.atInfo()
                    .addKeyValue("eventId", event.eventId())
                    .addKeyValue("eventType", event.eventType())
                    .addKeyValue("topic", topic)
                    .addKeyValue("partitionKey", serialized.partitionKey())
                    .log("Published Ledger outbox event");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            scheduleRetry(event, exception);
        } catch (ExecutionException | TimeoutException exception) {
            scheduleRetry(event, exception);
        }
    }

    private void scheduleRetry(LedgerOutboxEvent event, Exception exception) {
        Instant retryAt = clock.instant().plus(retryDelay(event.attempts()));
        repository.scheduleRetry(event.eventId(), retryAt);
        LOGGER.atWarn()
                .addKeyValue("eventId", event.eventId())
                .addKeyValue("eventType", event.eventType())
                .addKeyValue("attempts", event.attempts() + 1)
                .addKeyValue("nextAttemptAt", retryAt)
                .setCause(exception)
                .log("Ledger outbox publication failed and was scheduled for retry");
    }

    private Duration retryDelay(int completedAttempts) {
        int exponent = Math.min(completedAttempts, 30);
        long multiplier = 1L << exponent;
        Duration calculated;
        try {
            calculated = initialRetryDelay.multipliedBy(multiplier);
        } catch (ArithmeticException exception) {
            return maxRetryDelay;
        }
        return calculated.compareTo(maxRetryDelay) > 0 ? maxRetryDelay : calculated;
    }
}
