package io.github.bayonle010.escrow.payment.messaging.outbox;

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

import io.github.bayonle010.escrow.payment.funding.entity.OutboxEventEntity;
import io.github.bayonle010.escrow.payment.funding.repository.OutboxEventRepository;
import tools.jackson.core.JacksonException;

@Component
@ConditionalOnProperty(
        name = "payment.outbox.publisher.enabled",
        havingValue = "true",
        matchIfMissing = true)
public class PaymentOutboxPublisher {

    private static final Logger LOGGER = LoggerFactory.getLogger(PaymentOutboxPublisher.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;
    private final OutboxEventSerializer serializer;
    private final Clock clock;
    private final String topic;
    private final int batchSize;
    private final Duration publishTimeout;
    private final Duration initialRetryDelay;
    private final Duration maxRetryDelay;

    public PaymentOutboxPublisher(
            OutboxEventRepository repository,
            KafkaTemplate<String, String> kafkaTemplate,
            OutboxEventSerializer serializer,
            Clock clock,
            @Value("${payment.messaging.topics.events:payment.events.v1}") String topic,
            @Value("${payment.outbox.publisher.batch-size:100}") int batchSize,
            @Value("${payment.outbox.publisher.publish-timeout:5s}") Duration publishTimeout,
            @Value("${payment.outbox.publisher.initial-retry-delay:1s}") Duration initialRetryDelay,
            @Value("${payment.outbox.publisher.max-retry-delay:5m}") Duration maxRetryDelay) {
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

    @Scheduled(fixedDelayString = "${payment.outbox.publisher.poll-interval:500ms}")
    @Transactional
    public void publishDueEvents() {
        Instant batchStartedAt = clock.instant();
        List<OutboxEventEntity> events = repository.lockNextBatch(batchStartedAt, batchSize);
        for (OutboxEventEntity event : events) {
            publish(event);
        }
    }

    private void publish(OutboxEventEntity event) {
        SerializedOutboxEvent serialized;
        try {
            serialized = serializer.serialize(event);
        } catch (JacksonException | IllegalArgumentException exception) {
            event.markFailed();
            LOGGER.atError()
                    .addKeyValue("eventId", event.getEventId())
                    .addKeyValue("eventType", event.getEventType())
                    .setCause(exception)
                    .log("Outbox event cannot be serialized and will not be retried");
            return;
        }

        try {
            kafkaTemplate.send(topic, serialized.partitionKey(), serialized.value())
                    .get(publishTimeout.toMillis(), TimeUnit.MILLISECONDS);
            Instant publishedAt = clock.instant();
            event.markPublished(publishedAt);
            LOGGER.atInfo()
                    .addKeyValue("eventId", event.getEventId())
                    .addKeyValue("eventType", event.getEventType())
                    .addKeyValue("topic", topic)
                    .addKeyValue("partitionKey", serialized.partitionKey())
                    .log("Published payment outbox event");
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            scheduleRetry(event, exception);
        } catch (ExecutionException | TimeoutException exception) {
            scheduleRetry(event, exception);
        }
    }

    private void scheduleRetry(OutboxEventEntity event, Exception exception) {
        Instant retryAt = clock.instant().plus(retryDelay(event.getAttempts()));
        event.scheduleRetry(retryAt);
        LOGGER.atWarn()
                .addKeyValue("eventId", event.getEventId())
                .addKeyValue("eventType", event.getEventType())
                .addKeyValue("attempts", event.getAttempts())
                .addKeyValue("nextAttemptAt", retryAt)
                .setCause(exception)
                .log("Payment outbox publication failed and was scheduled for retry");
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
