package io.github.bayonle010.escrow.ledger.messaging.payment;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

import org.apache.kafka.clients.admin.Admin;
import org.apache.kafka.clients.admin.AdminClientConfig;
import org.apache.kafka.clients.admin.NewTopic;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.common.serialization.StringSerializer;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.kafka.KafkaContainer;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "ledger.messaging.consumer-enabled=true",
    "spring.kafka.listener.concurrency=1"
})
class PaymentSucceededKafkaIntegrationTest {

    private static final String TOPIC = "payment.events.v1";
    private static final String CONSUMER_GROUP = "ledger-payment-integration";
    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("ledger_kafka_test")
            .withUsername("ledger_test")
            .withPassword("ledger_test");

    @Container
    static final KafkaContainer KAFKA = new KafkaContainer("apache/kafka:4.3.1");

    @DynamicPropertySource
    static void configureInfrastructure(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.kafka.bootstrap-servers", KAFKA::getBootstrapServers);
        registry.add("spring.kafka.consumer.group-id", () -> CONSUMER_GROUP);
    }

    @BeforeAll
    static void createTopic() throws Exception {
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()))) {
            admin.createTopics(List.of(new NewTopic(TOPIC, 3, (short) 1)))
                    .all()
                    .get(10, TimeUnit.SECONDS);
        }
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearLedger() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM ledger_account_balances");
        jdbcTemplate.update("DELETE FROM ledger_entries");
        jdbcTemplate.update("DELETE FROM ledger_journals");
        jdbcTemplate.update("DELETE FROM ledger_accounts");
        jdbcTemplate.update("DELETE FROM consumer_inbox");
    }

    @Test
    void duplicateKafkaDeliveryCreatesOneFinancialEffect() throws Exception {
        String eventJson = paymentSucceededEvent();

        try (KafkaProducer<String, String> producer = producer()) {
            ProducerRecord<String, String> record = new ProducerRecord<>(
                    TOPIC,
                    ESCROW_ID.toString(),
                    eventJson);
            producer.send(record).get(5, TimeUnit.SECONDS);
            producer.send(record).get(5, TimeUnit.SECONDS);
        }

        awaitBothRecordsCommitted();
        assertThat(count("consumer_inbox")).isEqualTo(1);
        assertThat(count("ledger_journals")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(2);
        assertThat(count("ledger_account_balances")).isEqualTo(2);
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(CASE side WHEN 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0) "
                        + "FROM ledger_entries",
                Long.class)).isZero();
    }

    private KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    private void awaitBothRecordsCommitted() throws Exception {
        Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
        try (Admin admin = Admin.create(Map.of(
                AdminClientConfig.BOOTSTRAP_SERVERS_CONFIG,
                KAFKA.getBootstrapServers()))) {
            while (Instant.now().isBefore(deadline)) {
                long committedOffsets = admin.listConsumerGroupOffsets(CONSUMER_GROUP)
                        .partitionsToOffsetAndMetadata()
                        .get(2, TimeUnit.SECONDS)
                        .values()
                        .stream()
                        .mapToLong(metadata -> metadata.offset())
                        .sum();
                if (committedOffsets >= 2) {
                    return;
                }
                Thread.sleep(50);
            }
        }
        throw new AssertionError("Timed out waiting for Ledger to commit both Kafka records.");
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private String paymentSucceededEvent() {
        return """
                {
                  "eventId":"%s",
                  "aggregateType":"Payment",
                  "aggregateId":"%s",
                  "eventType":"PaymentSucceeded",
                  "eventVersion":1,
                  "occurredAt":"2026-08-28T00:00:00Z",
                  "correlationId":"%s",
                  "payload":{
                    "eventType":"PaymentSucceeded",
                    "eventVersion":1,
                    "occurredAt":"2026-08-28T00:00:00Z",
                    "paymentId":"%s",
                    "escrowId":"%s",
                    "payerId":"019c0000-0000-7000-8000-000000000001",
                    "amountMinor":100000,
                    "currency":"NGN",
                    "provider":"SIMULATED",
                    "providerReference":"simulated-transaction-1001",
                    "status":"SUCCEEDED",
                    "aggregateVersion":1,
                    "correlationId":"%s"
                  }
                }
                """.formatted(
                EVENT_ID,
                PAYMENT_ID,
                CORRELATION_ID,
                PAYMENT_ID,
                ESCROW_ID,
                CORRELATION_ID);
    }
}
