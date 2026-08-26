package io.github.bayonle010.escrow.escrow.acceptance;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.UUID;

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
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EscrowTermsAcceptanceApiIntegrationTest {

    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("escrow_acceptance_test")
            .withUsername("escrow_test")
            .withPassword("escrow_test");

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
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM escrow_terms_acceptances");
        jdbcTemplate.update("DELETE FROM escrow_terms");
        jdbcTemplate.update("DELETE FROM escrows");
    }

    @Test
    void acceptsCurrentTermsAndCommitsStateAcceptanceAndOutboxAtomically()
            throws IOException, InterruptedException {
        UUID escrowId = createEscrow();

        HttpResponse<String> response = acceptTerms(escrowId, SELLER_ID, 1);

        assertThat(response.statusCode()).isEqualTo(200);
        String correlationId = response.headers()
                .firstValue("X-Correlation-Id")
                .orElseThrow();
        assertThat(response.body())
                .contains("\"escrowId\":\"" + escrowId + "\"")
                .contains("\"acceptedBy\":\"" + SELLER_ID + "\"")
                .contains("\"termsVersion\":1")
                .contains("\"state\":\"AWAITING_FUNDING\"");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM escrows WHERE escrow_id = ?",
                String.class,
                escrowId)).isEqualTo("AWAITING_FUNDING");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT participant_id FROM escrow_terms_acceptances WHERE escrow_id = ?",
                UUID.class,
                escrowId)).isEqualTo(SELLER_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                escrowId)).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM outbox_events "
                        + "WHERE aggregate_id = ? AND event_type = 'EscrowTermsAccepted'",
                UUID.class,
                escrowId).toString()).isEqualTo(correlationId);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events "
                        + "WHERE aggregate_id = ? AND event_type = 'EscrowTermsAccepted'",
                String.class,
                escrowId))
                .contains("EscrowTermsAccepted")
                .contains(SELLER_ID.toString())
                .contains("AWAITING_FUNDING");
    }

    @Test
    void rejectsTheCreatorAndLeavesTheEscrowUnchanged()
            throws IOException, InterruptedException {
        UUID escrowId = createEscrow();

        HttpResponse<String> response = acceptTerms(escrowId, BUYER_ID, 1);

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("ESCROW_ACCEPTOR_NOT_COUNTERPARTY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT state FROM escrows WHERE escrow_id = ?",
                String.class,
                escrowId)).isEqualTo("AWAITING_COUNTERPARTY");
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM escrow_terms_acceptances",
                Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM outbox_events WHERE aggregate_id = ?",
                Integer.class,
                escrowId)).isEqualTo(1);
    }

    private UUID createEscrow() throws IOException, InterruptedException {
        String body = """
                {
                  "buyerId":"%s",
                  "sellerId":"%s",
                  "createdBy":"%s",
                  "amountMinor":100000,
                  "currency":"NGN",
                  "description":"Professional camera",
                  "category":"GOODS",
                  "deliveryDeadline":"2099-09-30T12:00:00Z",
                  "inspectionPeriodDays":7,
                  "releaseConditions":"Release after accepted delivery",
                  "refundConditions":"Refund if delivery misses the deadline"
                }
                """.formatted(BUYER_ID, SELLER_ID, BUYER_ID);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/escrows"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(201);
        String location = response.headers().firstValue("Location").orElseThrow();
        return UUID.fromString(location.substring(location.lastIndexOf('/') + 1));
    }

    private HttpResponse<String> acceptTerms(UUID escrowId, UUID participantId, int termsVersion)
            throws IOException, InterruptedException {
        String body = """
                {
                  "participantId":"%s",
                  "termsVersion":%d
                }
                """.formatted(participantId, termsVersion);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port
                        + "/api/v1/escrows/" + escrowId + "/accept-terms"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
