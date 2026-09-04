package io.github.bayonle010.escrow.payment.funding;

import static org.assertj.core.api.Assertions.assertThat;

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
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

                        @Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
            "payment-provider.simulated.callback-secret=integration-callback-secret",
            "payment.outbox.publisher.enabled=false"
        })
class PaymentConfirmationApiIntegrationTest {

    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID SECOND_PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000031");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID SECOND_ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000021");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final String CALLBACK_SECRET = "integration-callback-secret";
    private static final String PROVIDER_REFERENCE = "simulated-transaction-1001";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("payment_confirmation_test")
            .withUsername("payment_test")
            .withPassword("payment_test");

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

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM payments");
        insertPayment(PAYMENT_ID, ESCROW_ID, "8e03978e-40d5-43e8-bc93-6894a57f9324");
    }

    @Test
    void commitsOneSucceededPaymentAndEventAcrossDuplicateCallbacks()
            throws IOException, InterruptedException {
        HttpResponse<String> first = confirm(PAYMENT_ID, PROVIDER_REFERENCE, CALLBACK_SECRET);
        HttpResponse<String> replay = confirm(PAYMENT_ID, PROVIDER_REFERENCE, CALLBACK_SECRET);

        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(replay.statusCode()).isEqualTo(200);
        assertThat(first.body()).contains("\"status\":\"SUCCEEDED\"")
                .contains("\"replayed\":false");
        assertThat(replay.body()).contains("\"replayed\":true");
        assertSucceededOnce(PAYMENT_ID);
    }

    @Test
    void serializesConcurrentDuplicateCallbacks() throws Exception {
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<HttpResponse<String>> first = executor.submit(
                    () -> confirmWhenReleased(ready, start));
            Future<HttpResponse<String>> second = executor.submit(
                    () -> confirmWhenReleased(ready, start));
            ready.await();
            start.countDown();

            List<HttpResponse<String>> responses = List.of(first.get(), second.get());
            assertThat(responses).allSatisfy(response -> assertThat(response.statusCode()).isEqualTo(200));
            assertThat(responses).extracting(HttpResponse::body)
                    .anySatisfy(body -> assertThat(body).contains("\"replayed\":false"))
                    .anySatisfy(body -> assertThat(body).contains("\"replayed\":true"));
            assertSucceededOnce(PAYMENT_ID);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void rejectsAnUnauthenticatedCallbackWithoutChangingThePayment()
            throws IOException, InterruptedException {
        HttpResponse<String> response = confirm(PAYMENT_ID, PROVIDER_REFERENCE, "wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("PROVIDER_CALLBACK_UNAUTHORIZED");
        assertThat(paymentStatus(PAYMENT_ID)).isEqualTo("PROCESSING");
        assertThat(eventCount("PaymentSucceeded")).isZero();
    }

    @Test
    void preventsAProviderReferenceFromFundingTwoPayments()
            throws IOException, InterruptedException {
        insertPayment(
                SECOND_PAYMENT_ID,
                SECOND_ESCROW_ID,
                "7f86176e-48b9-4da7-97e9-8e0f312f65af");
        assertThat(confirm(PAYMENT_ID, PROVIDER_REFERENCE, CALLBACK_SECRET).statusCode())
                .isEqualTo(200);

        HttpResponse<String> response = confirm(
                SECOND_PAYMENT_ID,
                PROVIDER_REFERENCE,
                CALLBACK_SECRET);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("PROVIDER_REFERENCE_REUSED");
        assertThat(paymentStatus(SECOND_PAYMENT_ID)).isEqualTo("PROCESSING");
        assertThat(eventCount("PaymentSucceeded")).isEqualTo(1);
    }

    private HttpResponse<String> confirmWhenReleased(CountDownLatch ready, CountDownLatch start)
            throws Exception {
        ready.countDown();
        start.await();
        return confirm(PAYMENT_ID, PROVIDER_REFERENCE, CALLBACK_SECRET);
    }

    private HttpResponse<String> confirm(
            UUID paymentId,
            String providerReference,
            String callbackSecret) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/v1/providers/simulated/payments/" + paymentId + "/confirm"))
                .header("Content-Type", "application/json")
                .header("X-Simulated-Provider-Secret", callbackSecret)
                .POST(HttpRequest.BodyPublishers.ofString(
                        "{\"providerReference\":\"" + providerReference + "\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    private void insertPayment(UUID paymentId, UUID escrowId, String idempotencyKey) {
        Instant now = Instant.parse("2026-08-28T00:00:00Z");
        jdbcTemplate.update(
                """
                INSERT INTO payments (
                    payment_id, escrow_id, payer_id, amount_minor, currency, provider,
                    status, idempotency_key, request_fingerprint, created_at, updated_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """,
                paymentId,
                escrowId,
                BUYER_ID,
                100000,
                "NGN",
                "SIMULATED",
                "PROCESSING",
                idempotencyKey,
                "a".repeat(64),
                now,
                now,
                0);
    }

    private void assertSucceededOnce(UUID paymentId) {
        assertThat(paymentStatus(paymentId)).isEqualTo("SUCCEEDED");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT provider_reference FROM payments WHERE payment_id = ?",
                String.class,
                paymentId)).isEqualTo(PROVIDER_REFERENCE);
        assertThat(eventCount("PaymentSucceeded")).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload ->> 'providerReference' FROM outbox_events "
                        + "WHERE event_type = 'PaymentSucceeded'",
                String.class)).isEqualTo(PROVIDER_REFERENCE);
    }

    private String paymentStatus(UUID paymentId) {
        return jdbcTemplate.queryForObject(
                "SELECT status FROM payments WHERE payment_id = ?",
                String.class,
                paymentId);
    }

    private int eventCount(String eventType) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE event_type = ?",
                Integer.class,
                eventType);
    }
}
