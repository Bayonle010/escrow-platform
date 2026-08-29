package io.github.bayonle010.escrow.ledger.funding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "ledger.internal-event-secret=integration-secret")
class LedgerFundingApiIntegrationTest {

    private static final UUID EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000040");
    private static final UUID SECOND_EVENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000041");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final String SECRET = "integration-secret";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("ledger_funding_test")
            .withUsername("ledger_test")
            .withPassword("ledger_test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @LocalServerPort
    private int port;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.execute(
                "TRUNCATE outbox_events, consumer_inbox, ledger_entries, ledger_journals, "
                        + "ledger_account_balances, ledger_accounts");
    }

    @Test
    void atomicallyPostsBalancedFundingAndOutboxEvent() throws IOException, InterruptedException {
        HttpResponse<String> response = post(EVENT_ID, PAYMENT_ID, 100000, SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).doesNotContain("\"status\"")
                .contains("\"replayed\":false");
        assertThat(count("ledger_journals")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(2);
        assertThat(count("ledger_accounts")).isEqualTo(2);
        assertThat(count("ledger_account_balances")).isEqualTo(2);
        assertThat(count("consumer_inbox")).isEqualTo(1);
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(entryTotal("DEBIT")).isEqualTo(100000);
        assertThat(entryTotal("CREDIT")).isEqualTo(100000);
        assertThat(jdbcTemplate.queryForList(
                "SELECT posted_balance_minor FROM ledger_account_balances",
                Long.class)).containsExactlyInAnyOrder(100000L, 100000L);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload ->> 'eventType' FROM outbox_events",
                String.class)).isEqualTo("EscrowFundingSecured");
    }

    @Test
    void deduplicatesSequentialAndConcurrentDelivery() throws Exception {
        HttpResponse<String> first = post(EVENT_ID, PAYMENT_ID, 100000, SECRET);
        HttpResponse<String> replay = post(EVENT_ID, PAYMENT_ID, 100000, SECRET);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(replay.body()).contains("\"replayed\":true");
        assertPostedOnce();

        setUp();
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> concurrentFirst = executor.submit(
                    () -> postWhenReleased(ready, start));
            Future<HttpResponse<String>> concurrentSecond = executor.submit(
                    () -> postWhenReleased(ready, start));
            ready.await();
            start.countDown();

            List<HttpResponse<String>> responses = List.of(concurrentFirst.get(), concurrentSecond.get());
            assertThat(responses).allSatisfy(result -> assertThat(result.statusCode()).isEqualTo(200));
            assertThat(responses).extracting(HttpResponse::body)
                    .anySatisfy(body -> assertThat(body).contains("\"replayed\":false"))
                    .anySatisfy(body -> assertThat(body).contains("\"replayed\":true"));
            assertPostedOnce();
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsConflictingDuplicatePaymentWithoutMovingMoneyAgain()
            throws IOException, InterruptedException {
        assertThat(post(EVENT_ID, PAYMENT_ID, 100000, SECRET).statusCode()).isEqualTo(200);

        HttpResponse<String> conflict = post(SECOND_EVENT_ID, PAYMENT_ID, 100001, SECRET);

        assertThat(conflict.statusCode()).isEqualTo(409);
        assertThat(conflict.body()).contains("FUNDING_EVENT_CONFLICT");
        assertPostedOnce();
        assertThat(count("consumer_inbox")).isEqualTo(1);
    }

    @Test
    void rejectsUnauthenticatedDeliveryWithoutWritingAnything()
            throws IOException, InterruptedException {
        HttpResponse<String> response = post(EVENT_ID, PAYMENT_ID, 100000, "wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("INTERNAL_EVENT_UNAUTHORIZED");
        assertThat(count("ledger_journals")).isZero();
        assertThat(count("consumer_inbox")).isZero();
    }

    @Test
    void databaseRejectsAnUnbalancedPostedJournalAtCommit() {
        UUID journalId = UUID.randomUUID();
        UUID accountId = UUID.randomUUID();
        Instant now = Instant.parse("2026-08-28T00:00:00Z");

        assertThatThrownBy(() -> transactionTemplate.executeWithoutResult(status -> {
            jdbcTemplate.update(
                    """
                    INSERT INTO ledger_accounts (
                        account_id, owner_type, owner_reference, account_type,
                        normal_side, currency, status, created_at, version
                    ) VALUES (?, 'PROVIDER', 'SIMULATED', 'PROVIDER_CLEARING',
                              'DEBIT', 'NGN', 'ACTIVE', ?, 0)
                    """,
                    accountId,
                    now);
            jdbcTemplate.update(
                    """
                    INSERT INTO ledger_journals (
                        journal_id, business_reference, journal_type, payment_id, escrow_id,
                        amount_minor, currency, provider, provider_reference,
                        correlation_id, causation_id, created_at
                    ) VALUES (?, ?, 'ESCROW_FUNDING', ?, ?, 100000, 'NGN', 'SIMULATED',
                              'provider-reference', ?, ?, ?)
                    """,
                    journalId,
                    "FUNDING:" + UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    UUID.randomUUID(),
                    now);
            jdbcTemplate.update(
                    """
                    INSERT INTO ledger_entries (
                        entry_id, journal_id, account_id, direction,
                        amount_minor, currency, sequence, created_at
                    ) VALUES (?, ?, ?, 'DEBIT', 100000, 'NGN', 1, ?)
                    """,
                    UUID.randomUUID(),
                    journalId,
                    accountId,
                    now);
        })).hasStackTraceContaining("is not balanced");

        assertThat(count("ledger_journals")).isZero();
        assertThat(count("ledger_entries")).isZero();
    }

    private HttpResponse<String> postWhenReleased(CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return post(EVENT_ID, PAYMENT_ID, 100000, SECRET);
    }

    private HttpResponse<String> post(
            UUID eventId,
            UUID paymentId,
            long amountMinor,
            String secret) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/internal/v1/ledger/events/payment-succeeded"))
                .header("Content-Type", "application/json")
                .header("X-Internal-Event-Secret", secret)
                .POST(HttpRequest.BodyPublishers.ofString(eventJson(eventId, paymentId, amountMinor)))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private String eventJson(UUID eventId, UUID paymentId, long amountMinor) {
        return """
                {
                  "eventId":"%s",
                  "eventVersion":1,
                  "occurredAt":"2026-08-28T00:00:00Z",
                  "paymentId":"%s",
                  "escrowId":"%s",
                  "payerId":"019c0000-0000-7000-8000-000000000001",
                  "amountMinor":%d,
                  "currency":"NGN",
                  "provider":"SIMULATED",
                  "providerReference":"simulated-transaction-1001",
                  "aggregateVersion":1,
                  "correlationId":"019c0000-0000-7000-8000-000000000010"
                }
                """.formatted(eventId, paymentId, ESCROW_ID, amountMinor);
    }

    private void assertPostedOnce() {
        assertThat(count("ledger_journals")).isEqualTo(1);
        assertThat(count("ledger_entries")).isEqualTo(2);
        assertThat(count("outbox_events")).isEqualTo(1);
        assertThat(entryTotal("DEBIT")).isEqualTo(entryTotal("CREDIT"));
    }

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }

    private long entryTotal(String direction) {
        return jdbcTemplate.queryForObject(
                "SELECT COALESCE(SUM(amount_minor), 0) FROM ledger_entries WHERE direction = ?",
                Long.class,
                direction);
    }
}
