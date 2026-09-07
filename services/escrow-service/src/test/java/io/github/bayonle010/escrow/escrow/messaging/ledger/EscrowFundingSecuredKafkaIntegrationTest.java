package io.github.bayonle010.escrow.escrow.messaging.ledger;

import static org.assertj.core.api.Assertions.assertThat;

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
    "escrow.messaging.consumer-enabled=true",
    "spring.kafka.consumer.group-id=escrow-funding-integration",
    "spring.kafka.listener.concurrency=1"
})
class EscrowFundingSecuredKafkaIntegrationTest {

    private static final String TOPIC = "ledger.events.v1";
    private static final UUID EVENT_ID = UUID.fromString("01a06bee-2621-7603-812c-4bad587d6956");
    private static final UUID JOURNAL_ID = UUID.fromString("01a06bee-25bd-7d14-96bb-87413b42230f");
    private static final UUID PAYMENT_ID = UUID.fromString("01a069da-eddf-7820-be8d-6c60a54c3259");
    private static final UUID ESCROW_ID = UUID.fromString("01a069d1-82fc-70f5-a5d4-21f81e9f7c4c");
    private static final UUID BUYER_ID = UUID.fromString("01a069ce-7bfb-7be7-8b1b-4208a8234c5c");
    private static final UUID SELLER_ID = UUID.fromString("01a069cf-67ef-7263-b156-8b4ff5018d56");
    private static final UUID CORRELATION_ID = UUID.fromString("01a069dd-45ee-752c-806f-51b8ef43497c");
    private static final UUID CAUSATION_ID = UUID.fromString("01a069dd-467e-74c2-9435-c233b0f0f3a0");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("escrow_funding_consumer_test")
            .withUsername("escrow_test")
            .withPassword("escrow_test");

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
        jdbcTemplate.update("DELETE FROM consumer_inbox");
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM escrow_terms_acceptances");
        jdbcTemplate.update("DELETE FROM escrow_terms");
        jdbcTemplate.update("DELETE FROM escrows");
        insertEscrowAwaitingFunding();
    }

    @Test
    void duplicateKafkaDeliveryTransitionsAndPublishesExactlyOnce() throws Exception {
        String eventJson = eventJson();
        try (KafkaProducer<String, String> producer = producer()) {
            producer.send(new ProducerRecord<>(TOPIC, ESCROW_ID.toString(), eventJson))
                    .get(10, TimeUnit.SECONDS);
            producer.send(new ProducerRecord<>(TOPIC, ESCROW_ID.toString(), eventJson))
                    .get(10, TimeUnit.SECONDS);
        }

        awaitFundingApplied();

        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM escrows WHERE escrow_id = ?",
                String.class,
                ESCROW_ID)).isEqualTo("FUNDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT version FROM escrows WHERE escrow_id = ?",
                Long.class,
                ESCROW_ID)).isEqualTo(1L);
        assertThat(count("consumer_inbox")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'EscrowFunded'",
                Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT causation_id FROM outbox_events WHERE event_type = 'EscrowFunded'",
                UUID.class)).isEqualTo(EVENT_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events WHERE event_type = 'EscrowFunded'",
                String.class))
                .contains("\"state\": \"FUNDED\"")
                .contains(JOURNAL_ID.toString())
                .contains(PAYMENT_ID.toString());
    }

    private KafkaProducer<String, String> producer() {
        return new KafkaProducer<>(Map.of(
                ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, KAFKA.getBootstrapServers(),
                ProducerConfig.ACKS_CONFIG, "all",
                ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class,
                ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, StringSerializer.class));
    }

    private void awaitFundingApplied() throws InterruptedException {
        Instant deadline = Instant.now().plusSeconds(10);
        while (Instant.now().isBefore(deadline)) {
            Integer inboxCount = count("consumer_inbox");
            Integer eventCount = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE event_type = 'EscrowFunded'",
                    Integer.class);
            if (inboxCount == 1 && eventCount == 1) {
                return;
            }
            Thread.sleep(50);
        }
        throw new AssertionError("Timed out waiting for EscrowFundingSecured processing.");
    }

    private Integer count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private void insertEscrowAwaitingFunding() {
        Instant now = Instant.parse("2026-09-04T10:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO escrows (
                    escrow_id, buyer_id, seller_id, current_terms_version, state,
                    amount_minor, currency, inspection_period_days, delivery_deadline,
                    created_at, updated_at, version
                ) VALUES (?, ?, ?, 1, 'AWAITING_FUNDING', 100000, 'NGN', 7, ?, ?, ?, 0)
                """,
                ESCROW_ID,
                BUYER_ID,
                SELLER_ID,
                Instant.parse("2099-09-30T12:00:00Z"),
                now,
                now);
    }

    private String eventJson() {
        return """
                {
                  "eventId":"%s",
                  "aggregateType":"LedgerJournal",
                  "aggregateId":"%s",
                  "eventType":"EscrowFundingSecured",
                  "eventVersion":1,
                  "occurredAt":"2026-09-04T10:19:24.448020Z",
                  "correlationId":"%s",
                  "causationId":"%s",
                  "payload":{
                    "eventType":"EscrowFundingSecured",
                    "eventVersion":1,
                    "occurredAt":"2026-09-04T10:19:24.448020457Z",
                    "journalId":"%s",
                    "paymentId":"%s",
                    "escrowId":"%s",
                    "amountMinor":100000,
                    "currency":"NGN",
                    "correlationId":"%s",
                    "causationId":"%s"
                  }
                }
                """.formatted(
                EVENT_ID, JOURNAL_ID, CORRELATION_ID, CAUSATION_ID,
                JOURNAL_ID, PAYMENT_ID, ESCROW_ID, CORRELATION_ID, CAUSATION_ID);
    }
}
