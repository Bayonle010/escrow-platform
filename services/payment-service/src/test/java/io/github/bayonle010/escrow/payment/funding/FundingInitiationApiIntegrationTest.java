package io.github.bayonle010.escrow.payment.funding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

import io.github.bayonle010.escrow.payment.funding.domain.EscrowFundingSnapshot;
import io.github.bayonle010.escrow.payment.funding.gateway.EscrowFundingSnapshotGateway;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = "payment.outbox.publisher.enabled=false")
class FundingInitiationApiIntegrationTest {

    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");
    private static final String IDEMPOTENCY_KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";
    private static final String SECOND_IDEMPOTENCY_KEY = "7f86176e-48b9-4da7-97e9-8e0f312f65af";

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("payment_funding_test")
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

    @MockitoBean
    private EscrowFundingSnapshotGateway escrowGateway;

    private final HttpClient httpClient = HttpClient.newHttpClient();

    @BeforeEach
    void setUp() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM payments");
        when(escrowGateway.get(eq(ESCROW_ID), any(UUID.class)))
                .thenReturn(new EscrowFundingSnapshot(
                        ESCROW_ID,
                        BUYER_ID,
                        SELLER_ID,
                        100000,
                        "NGN",
                        1,
                        "AWAITING_FUNDING",
                        Instant.parse("2099-09-30T12:00:00Z"),
                        Instant.parse("2026-08-20T12:00:00Z"),
                        Instant.parse("2026-08-21T12:00:00Z")));
    }

    @Test
    void commitsThePaymentAndOutboxAndSafelyReplaysTheRequest()
            throws IOException, InterruptedException {
        HttpResponse<String> first = initiate(IDEMPOTENCY_KEY);
        HttpResponse<String> replay = initiate(IDEMPOTENCY_KEY);

        assertThat(first.statusCode()).isEqualTo(202);
        assertThat(replay.statusCode()).isEqualTo(202);
        assertThat(first.headers().firstValue("Location"))
                .isEqualTo(replay.headers().firstValue("Location"));
        assertThat(first.body()).contains("\"status\":\"PROCESSING\"")
                .contains("\"replayed\":false");
        assertThat(replay.body()).contains("\"replayed\":true");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class))
                .isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT event_type FROM outbox_events",
                String.class)).isEqualTo("FundingInitiated");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload ->> 'escrowId' FROM outbox_events",
                String.class)).isEqualTo(ESCROW_ID.toString());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload ->> 'status' FROM outbox_events",
                String.class)).isEqualTo("PROCESSING");
    }

    @Test
    void rejectsASecondFundingInstructionForTheSameEscrow() throws IOException, InterruptedException {
        assertThat(initiate(IDEMPOTENCY_KEY).statusCode()).isEqualTo(202);

        HttpResponse<String> response = initiate(SECOND_IDEMPOTENCY_KEY);

        assertThat(response.statusCode()).isEqualTo(409);
        assertThat(response.body()).contains("PAYMENT_ALREADY_EXISTS");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM payments", Integer.class))
                .isEqualTo(1);
    }

    private HttpResponse<String> initiate(String idempotencyKey) throws IOException, InterruptedException {
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/escrows/" + ESCROW_ID + "/fund"))
                .header("Content-Type", "application/json")
                .header("Idempotency-Key", idempotencyKey)
                .POST(HttpRequest.BodyPublishers.ofString("{\"payerId\":\"" + BUYER_ID + "\"}"))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
