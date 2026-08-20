package io.github.bayonle010.escrow.identity.registration;

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
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class RegistrationApiIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("identity_test")
            .withUsername("identity_test")
            .withPassword("identity_test");

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
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void clearDatabase() {
        jdbcTemplate.update("DELETE FROM outbox_events");
        jdbcTemplate.update("DELETE FROM users");
    }

    @Test
    void registersUserAndOutboxEventInOneTransaction() throws IOException, InterruptedException {
        HttpResponse<String> response = register("Alice@Example.COM", "A-secure-password1!");

        assertThat(response.statusCode()).isEqualTo(201);
        assertThat(response.headers().firstValue("Location")).hasValueSatisfying(
                value -> assertThat(value).startsWith("/api/v1/users/"));
        assertThat(response.headers().firstValue("X-Correlation-Id")).isPresent();
        String responseCorrelationId = response.headers().firstValue("X-Correlation-Id").orElseThrow();
        assertThat(response.body())
                .contains("\"email\":\"alice@example.com\"")
                .contains("\"status\":\"PENDING_VERIFICATION\"")
                .doesNotContain("A-secure-password1!");

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
        String passwordHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE normalized_email = 'alice@example.com'",
                String.class);
        assertThat(passwordEncoder.matches("A-secure-password1!", passwordHash)).isTrue();

        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);
        UUID correlationId = jdbcTemplate.queryForObject(
                "SELECT correlation_id FROM outbox_events",
                UUID.class);
        assertThat(correlationId).isNotNull();
        assertThat(correlationId.version()).isEqualTo(7);
        assertThat(correlationId.toString()).isEqualTo(responseCorrelationId);
        String eventPayload = jdbcTemplate.queryForObject(
                "SELECT payload::text FROM outbox_events",
                String.class);
        assertThat(eventPayload)
                .contains("UserRegistered")
                .contains("alice@example.com")
                .doesNotContain("A-secure-password1!")
                .doesNotContain(passwordHash);
    }

    @Test
    void rejectsDuplicateNormalizedEmailWithoutCreatingAnotherEvent()
            throws IOException, InterruptedException {
        assertThat(register("alice@example.com", "A-secure-password1!").statusCode()).isEqualTo(201);

        HttpResponse<String> duplicate = register("ALICE@example.com", "Another-secure-password2!");

        assertThat(duplicate.statusCode()).isEqualTo(409);
        assertThat(duplicate.body()).contains("IDENTITY_EMAIL_ALREADY_REGISTERED");
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM users", Integer.class)).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject("SELECT COUNT(*) FROM outbox_events", Integer.class)).isEqualTo(1);
    }

    private HttpResponse<String> register(String email, String password)
            throws IOException, InterruptedException {
        String body = """
                {"email":"%s","password":"%s"}
                """.formatted(email, password);
        HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + port + "/api/v1/auth/register"))
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();
        return HttpClient.newHttpClient().send(request, HttpResponse.BodyHandlers.ofString());
    }
}
