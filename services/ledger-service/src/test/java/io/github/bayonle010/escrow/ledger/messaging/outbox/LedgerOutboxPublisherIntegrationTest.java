package io.github.bayonle010.escrow.ledger.messaging.outbox;

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
import org.apache.kafka.clients.consumer.ConsumerConfig;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.serialization.StringDeserializer;
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

import tools.jackson.databind.json.JsonMapper;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "ledger.messaging.consumer-enabled=false",
    "ledger.outbox.publisher.poll-interval=100ms",
    "ledger.outbox.publisher.publish-timeout=2s"
})
class LedgerOutboxPublisherIntegrationTest {

    private static final String TOPIC = "ledger.events.v1";
    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000060");
    private static final UUID JOURNAL_ID = UUID.fromString("019c0000-0000-7000-8000-000000000050");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID CORRELATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000010");
    private static final UUID CAUSATION_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("ledger_outbox_test")
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
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_events");
    }

    @Test
    void eventuallyPublishesOneRecordAndCommitsTheOutboxState() throws Exception {
        insertDueOutboxEvent();

        try (KafkaConsumer<String, String> consumer = consumer()) {
            consumer.subscribe(List.of(TOPIC));
            var records = pollUntilRecordArrives(consumer);

            assertThat(records.count()).isEqualTo(1);
            var record = records.iterator().next();
            assertThat(record.key()).isEqualTo(ESCROW_ID.toString());
            var envelope = JsonMapper.builder().findAndAddModules().build().readTree(record.value());
            assertThat(envelope.get("eventId").asString()).isEqualTo(EVENT_ID.toString());
            assertThat(envelope.get("aggregateId").asString()).isEqualTo(JOURNAL_ID.toString());
            assertThat(envelope.get("eventType").asString()).isEqualTo("EscrowFundingSecured");
            assertThat(envelope.get("causationId").asString()).isEqualTo(CAUSATION_ID.toString());
            assertThat(envelope.get("payload").get("escrowId").asString())
                    .isEqualTo(ESCROW_ID.toString());

            awaitPublishedState();
            assertThat(consumer.poll(Duration.ofSeconds(1)).isEmpty()).isTrue();
        }
    }

    private KafkaConsumer<String, String> consumer() {
        return new KafkaConsumer<>(Map.of(
                ConsumerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ConsumerConfig.GROUP_ID_CONFIG, "ledger-outbox-integration-" + UUID.randomUUID(),
                ConsumerConfig.AUTO_OFFSET_RESET_CONFIG, "earliest",
                ConsumerConfig.KEY_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class,
                ConsumerConfig.VALUE_DESERIALIZER_CLASS_CONFIG, StringDeserializer.class));
    }

    private org.apache.kafka.clients.consumer.ConsumerRecords<String, String> pollUntilRecordArrives(
            KafkaConsumer<String, String> consumer) {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            var records = consumer.poll(Duration.ofMillis(250));
            if (!records.isEmpty()) {
                return records;
            }
        }
        throw new AssertionError("Timed out waiting for the Ledger outbox record.");
    }

    private void awaitPublishedState() throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(5);
        while (Instant.now().isBefore(deadline)) {
            String status = jdbcTemplate.queryForObject(
                    "SELECT status FROM outbox_events WHERE event_id = ?",
                    String.class,
                    EVENT_ID);
            if ("PUBLISHED".equals(status)) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for the Ledger outbox row to become PUBLISHED.");
    }

    private void insertDueOutboxEvent() {
        Instant occurredAt = Instant.parse("2026-08-28T00:00:00.123456Z");
        String payload = """
                {
                  "eventType":"EscrowFundingSecured",
                  "eventVersion":1,
                  "occurredAt":"2026-08-28T00:00:00.123456Z",
                  "journalId":"%s",
                  "paymentId":"%s",
                  "escrowId":"%s",
                  "amountMinor":100000,
                  "currency":"NGN",
                  "correlationId":"%s",
                  "causationId":"%s"
                }
                """.formatted(JOURNAL_ID, PAYMENT_ID, ESCROW_ID, CORRELATION_ID, CAUSATION_ID);
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_id, aggregate_type, event_type, event_version,
                    partition_key, correlation_id, causation_id, payload, occurred_at,
                    status, attempts, next_attempt_at
                ) VALUES (?, ?, 'LedgerJournal', 'EscrowFundingSecured', 1, ?, ?, ?,
                          CAST(? AS jsonb), ?, 'PENDING', 0, ?)
                """,
                EVENT_ID,
                JOURNAL_ID,
                ESCROW_ID,
                CORRELATION_ID,
                CAUSATION_ID,
                payload,
                occurredAt,
                occurredAt);
    }
}
