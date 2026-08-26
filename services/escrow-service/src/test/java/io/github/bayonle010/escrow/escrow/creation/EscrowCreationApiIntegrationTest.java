package io.github.bayonle010.escrow.escrow.creation;

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
class EscrowCreationApiIntegrationTest {

    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("escrow_test")
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

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM escrow_terms_acceptances");
        jdbcTemplate.update("DELETE FROM escrow_terms");
        jdbcTemplate.update("DELETE FROM escrows");
    }

    @Test
    void createsEscrowTermsAndOutboxEventInOneTransaction() throws IOException, InterruptedException {
        HttpResponse<String> response = createEscrow(BUYER_ID, SELLER_ID, "ngn");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(value -> assertThat(value).startsWith("/api/v1/escrows/"));
        String responseCorrelationId = response.headers()
                .firstValue("X-Correlation-Id")
                .orElseThrow();
        assertThat(response.body())
                .contains("\"currency\":\"NGN\"")
                .contains("\"termsVersion\":1")
                .contains("\"state\":\"AWAITING_COUNTERPARTY\"");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM escrows", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM escrow_terms", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT current_terms_version FROM escrows", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT terms_version FROM escrow_terms", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_by FROM escrow_terms", UUID.class)).isEqualTo(BUYER_ID);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM outbox_events", UUID.class).toString())
                .isEqualTo(responseCorrelationId);
        String payload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events", String.class);
        assertThat(payload)
                .contains("EscrowCreated")
                .contains(BUYER_ID.toString())
                .contains(SELLER_ID.toString())
                .contains("AWAITING_COUNTERPARTY");
    }

    @Test
    void rejectsInvalidParticipantsWithoutPersistingAnything() throws IOException, InterruptedException {
        HttpResponse<String> response = createEscrow(BUYER_ID, BUYER_ID, "NGN");

        assertThat(response.statusCode()).isEqualTo(422);
        assertThat(response.body()).contains("ESCROW_PARTICIPANTS_MUST_DIFFER");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM escrows", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM escrow_terms", Integer.class)).isZero();
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isZero();
    }

    private HttpResponse<String> createEscrow(UUID buyerId, UUID sellerId, String currency)
            throws IOException, InterruptedException {
        String body = """
                {
                  "buyerId":"%s",
                  "sellerId":"%s",
                  "createdBy":"%s",
                  "amountMinor":100000,
                  "currency":"%s",
                  "description":"Professional camera",
                  "category":"goods",
                  "deliveryDeadline":"2099-09-30T12:00:00Z",
                  "inspectionPeriodDays":7,
                  "releaseConditions":"Release after accepted delivery",
                  "refundConditions":"Refund if delivery misses the deadline"
                }
                """.formatted(buyerId, sellerId, buyerId, currency);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/escrows"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
