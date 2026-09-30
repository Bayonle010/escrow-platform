package io.github.bayonle010.escrow.systemtest.funding;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.fail;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.BooleanSupplier;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class KafkaRecoverySystemIT {

    private static final Duration COMMAND_TIMEOUT = Duration.ofMinutes(10);
    private static final Duration HTTP_TIMEOUT = Duration.ofSeconds(15);
    private static final Duration FAILURE_ASSERTION_TIMEOUT = Duration.ofSeconds(30);
    private static final Duration RECOVERY_TIMEOUT = Duration.ofSeconds(90);
    private static final Duration POLL_INTERVAL = Duration.ofMillis(500);

    private static final Path PROJECT_ROOT = findProjectRoot();
    private static final JsonMapper JSON = JsonMapper.builder().build();
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(HTTP_TIMEOUT)
            .build();
    private static final String PROVIDER_SECRET = environment(
            "SIMULATED_PROVIDER_CALLBACK_SECRET",
            "local-development-secret");
    private static final String ESCROW_BASE_URL = environment(
            "ESCROW_BASE_URL",
            "http://localhost:8082");
    private static final String PAYMENT_BASE_URL = environment(
            "PAYMENT_BASE_URL",
            "http://localhost:8083");

    @BeforeAll
    static void startPlatform() {
        compose(
                "up",
                "--build",
                "--detach",
                "--wait",
                "escrow-service",
                "payment-service",
                "ledger-service");
    }

    @AfterAll
    static void leaveKafkaAvailable() {
        restoreKafka("after the system test");
    }

    @Test
    void paymentEventSurvivesKafkaOutageAndFundsEscrowExactlyOnce() throws Exception {
        UUID buyerId = UUID.randomUUID();
        UUID sellerId = UUID.randomUUID();
        UUID escrowId = createEscrow(buyerId, sellerId);
        acceptTerms(escrowId, sellerId);
        UUID paymentId = initiateFunding(escrowId, buyerId);

        assertEquals("AWAITING_FUNDING", escrowState(escrowId));

        compose("stop", "kafka");
        try {
            confirmPayment(paymentId);

            await(
                    "PaymentSucceeded to remain pending while Kafka is unavailable",
                    FAILURE_ASSERTION_TIMEOUT,
                    () -> paymentOutboxIsPending(paymentId));

            assertEquals("SUCCEEDED", paymentStatus(paymentId));
            assertEquals("0", ledgerScalar(
                    "SELECT COUNT(*) FROM ledger_journals WHERE payment_id = '" + paymentId + "'"));

            compose("start", "kafka");
            await(
                    "Kafka to accept internal client connections",
                    Duration.ofSeconds(60),
                    KafkaRecoverySystemIT::kafkaIsAvailable);

            await(
                    "escrow to become FUNDED after Kafka recovery",
                    RECOVERY_TIMEOUT,
                    () -> "FUNDED".equals(escrowState(escrowId)));

            assertFundingInvariants(paymentId, escrowId);
        } finally {
            restoreKafka("during test cleanup");
        }
    }

    private static UUID createEscrow(UUID buyerId, UUID sellerId) throws Exception {
        Map<String, Object> request = new LinkedHashMap<>();
        request.put("buyerId", buyerId);
        request.put("sellerId", sellerId);
        request.put("createdBy", buyerId);
        request.put("amountMinor", 3_500);
        request.put("currency", "NGN");
        request.put("description", "Kafka recovery system test");
        request.put("category", "GOODS");
        request.put("deliveryDeadline", Instant.now().plus(30, ChronoUnit.DAYS));
        request.put("inspectionPeriodDays", 7);
        request.put("releaseConditions", "Release after accepted delivery");
        request.put("refundConditions", "Refund if delivery misses the deadline");

        JsonNode response = sendJson(
                "POST",
                ESCROW_BASE_URL + "/api/v1/escrows",
                request,
                Map.of(),
                201);
        return UUID.fromString(requiredText(response, "data", "id"));
    }

    private static void acceptTerms(UUID escrowId, UUID sellerId) throws Exception {
        JsonNode response = sendJson(
                "POST",
                ESCROW_BASE_URL + "/api/v1/escrows/" + escrowId + "/accept-terms",
                Map.of("participantId", sellerId, "termsVersion", 1),
                Map.of(),
                200);
        assertEquals("AWAITING_FUNDING", requiredText(response, "data", "state"));
    }

    private static UUID initiateFunding(UUID escrowId, UUID buyerId) throws Exception {
        JsonNode response = sendJson(
                "POST",
                PAYMENT_BASE_URL + "/api/v1/escrows/" + escrowId + "/fund",
                Map.of("payerId", buyerId),
                Map.of("Idempotency-Key", UUID.randomUUID().toString()),
                202);
        return UUID.fromString(requiredText(response, "data", "id"));
    }

    private static void confirmPayment(UUID paymentId) throws Exception {
        JsonNode response = sendJson(
                "POST",
                PAYMENT_BASE_URL + "/api/v1/providers/simulated/payments/" + paymentId + "/confirm",
                Map.of("providerReference", "kafka-recovery-" + paymentId),
                Map.of("X-Simulated-Provider-Secret", PROVIDER_SECRET),
                200);
        assertEquals("SUCCEEDED", requiredText(response, "data", "status"));
    }

    private static JsonNode sendJson(
            String method,
            String uri,
            Object body,
            Map<String, String> headers,
            int expectedStatus) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create(uri))
                .timeout(HTTP_TIMEOUT)
                .header("Content-Type", "application/json");
        headers.forEach(request::header);
        request.method(method, HttpRequest.BodyPublishers.ofString(JSON.writeValueAsString(body)));

        HttpResponse<String> response = HTTP.send(
                request.build(),
                HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        assertEquals(
                expectedStatus,
                response.statusCode(),
                () -> method + " " + uri + " returned " + response.statusCode() + ": " + response.body());
        return JSON.readTree(response.body());
    }

    private static String escrowState(UUID escrowId) {
        try {
            HttpRequest request = HttpRequest.newBuilder(
                            URI.create(ESCROW_BASE_URL + "/api/v1/escrows/" + escrowId))
                    .timeout(HTTP_TIMEOUT)
                    .GET()
                    .build();
            HttpResponse<String> response = HTTP.send(
                    request,
                    HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
            if (response.statusCode() != 200) {
                return null;
            }
            return requiredText(JSON.readTree(response.body()), "data", "state");
        } catch (Exception exception) {
            return null;
        }
    }

    private static String paymentStatus(UUID paymentId) {
        return paymentScalar("SELECT status FROM payments WHERE payment_id = '" + paymentId + "'");
    }

    private static boolean paymentOutboxIsPending(UUID paymentId) {
        String result = paymentScalar("""
                SELECT status
                FROM outbox_events
                WHERE aggregate_id = '%s'
                  AND event_type = 'PaymentSucceeded'
                """.formatted(paymentId));
        return "PENDING".equals(result);
    }

    private static boolean kafkaIsAvailable() {
        CommandResult result = runCommand(
                Duration.ofSeconds(15),
                composeCommand(
                        "exec",
                        "-T",
                        "kafka",
                        "/opt/kafka/bin/kafka-broker-api-versions.sh",
                        "--bootstrap-server",
                        "kafka:19092"));
        return result.exitCode() == 0;
    }

    private static void assertFundingInvariants(UUID paymentId, UUID escrowId) {
        assertEquals("PUBLISHED|1", paymentScalar("""
                SELECT status || '|' || COUNT(*) OVER ()
                FROM outbox_events
                WHERE aggregate_id = '%s'
                  AND event_type = 'PaymentSucceeded'
                """.formatted(paymentId)));

        assertEquals("1|0", ledgerScalar("""
                SELECT COUNT(DISTINCT journal_id) || '|' ||
                       COALESCE(SUM(CASE WHEN direction = 'DEBIT' THEN amount_minor ELSE -amount_minor END), 0)
                FROM ledger_entries
                WHERE journal_id IN (
                    SELECT journal_id FROM ledger_journals WHERE payment_id = '%s'
                )
                """.formatted(paymentId)));

        assertEquals("1", ledgerScalar("""
                SELECT COUNT(*)
                FROM consumer_inbox
                WHERE aggregate_id = '%s'
                  AND event_type = 'PaymentSucceeded'
                """.formatted(paymentId)));

        String ledgerEventId = ledgerScalar("""
                SELECT event_id
                FROM outbox_events
                WHERE event_type = 'EscrowFundingSecured'
                  AND payload->>'escrowId' = '%s'
                """.formatted(escrowId));
        assertFalse(ledgerEventId.isBlank(), "Ledger did not create EscrowFundingSecured");

        assertEquals("PUBLISHED|1", ledgerScalar("""
                SELECT status || '|' || COUNT(*) OVER ()
                FROM outbox_events
                WHERE event_type = 'EscrowFundingSecured'
                  AND payload->>'escrowId' = '%s'
                """.formatted(escrowId)));

        assertEquals("1", escrowScalar("""
                SELECT COUNT(*)
                FROM consumer_inbox
                WHERE event_id = '%s'
                  AND event_type = 'EscrowFundingSecured'
                """.formatted(ledgerEventId)));

        assertEquals("1", escrowScalar("""
                SELECT COUNT(*)
                FROM outbox_events
                WHERE aggregate_id = '%s'
                  AND event_type = 'EscrowFunded'
                """.formatted(escrowId)));
        assertEquals("FUNDED", escrowScalar(
                "SELECT state FROM escrows WHERE escrow_id = '" + escrowId + "'"));
    }

    private static String paymentScalar(String sql) {
        return psql(
                "payment-postgres",
                environment("PAYMENT_DB_USER", "payment_local"),
                environment("PAYMENT_DB_NAME", "payment_db"),
                sql);
    }

    private static String ledgerScalar(String sql) {
        return psql(
                "ledger-postgres",
                environment("LEDGER_DB_USER", "ledger_local"),
                environment("LEDGER_DB_NAME", "ledger_db"),
                sql);
    }

    private static String escrowScalar(String sql) {
        return psql(
                "escrow-postgres",
                environment("ESCROW_DB_USER", "escrow_local"),
                environment("ESCROW_DB_NAME", "escrow_db"),
                sql);
    }

    private static String psql(String service, String username, String database, String sql) {
        CommandResult result = runCommand(
                Duration.ofSeconds(30),
                composeCommand(
                        "exec",
                        "-T",
                        service,
                        "psql",
                        "--username",
                        username,
                        "--dbname",
                        database,
                        "--tuples-only",
                        "--no-align",
                        "--set",
                        "ON_ERROR_STOP=1",
                        "--command",
                        sql));
        assertCommandSucceeded(result);
        return result.output().trim();
    }

    private static String requiredText(JsonNode root, String... path) {
        JsonNode current = root;
        for (String segment : path) {
            current = current.path(segment);
        }
        assertFalse(current.isMissingNode(), "Response is missing JSON path " + String.join(".", path));
        String value = current.asString();
        assertFalse(value == null || value.isBlank(), "JSON path is blank: " + String.join(".", path));
        return value;
    }

    private static void await(String description, Duration timeout, BooleanSupplier condition) {
        Instant deadline = Instant.now().plus(timeout);
        RuntimeException lastFailure = null;
        while (Instant.now().isBefore(deadline)) {
            try {
                if (condition.getAsBoolean()) {
                    return;
                }
            } catch (RuntimeException exception) {
                lastFailure = exception;
            }
            try {
                Thread.sleep(POLL_INTERVAL.toMillis());
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
                fail("Interrupted while waiting for " + description, exception);
            }
        }
        if (lastFailure == null) {
            fail("Timed out after " + timeout + " waiting for " + description);
        }
        fail("Timed out after " + timeout + " waiting for " + description, lastFailure);
    }

    private static void restoreKafka(String context) {
        CommandResult result = runCommand(
                Duration.ofMinutes(2),
                composeCommand("up", "--detach", "--wait", "kafka"));
        if (result.exitCode() != 0) {
            System.err.println("Failed to restore Kafka " + context + ":\n" + result.output());
        }
    }

    private static void compose(String... arguments) {
        CommandResult result = runCommand(COMMAND_TIMEOUT, composeCommand(arguments));
        assertCommandSucceeded(result);
    }

    private static List<String> composeCommand(String... arguments) {
        List<String> command = new ArrayList<>();
        command.add(environment("DOCKER_COMMAND", "docker"));
        command.add("compose");
        command.addAll(List.of(arguments));
        return command;
    }

    private static CommandResult runCommand(Duration timeout, List<String> command) {
        Process process = null;
        try {
            process = new ProcessBuilder(command)
                    .directory(PROJECT_ROOT.toFile())
                    .redirectErrorStream(true)
                    .start();
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            Process runningProcess = process;
            Thread outputReader = Thread.ofVirtual().start(() -> copyOutput(runningProcess, output));

            boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
            if (!finished) {
                process.destroyForcibly();
                process.waitFor(5, TimeUnit.SECONDS);
                outputReader.join(Duration.ofSeconds(5));
                return new CommandResult(
                        -1,
                        output.toString(StandardCharsets.UTF_8)
                                + System.lineSeparator()
                                + "Command timed out: "
                                + String.join(" ", command));
            }

            outputReader.join(Duration.ofSeconds(5));
            return new CommandResult(process.exitValue(), output.toString(StandardCharsets.UTF_8));
        } catch (IOException exception) {
            return new CommandResult(-1, "Could not run " + String.join(" ", command) + ": " + exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            if (process != null) {
                process.destroyForcibly();
            }
            return new CommandResult(-1, "Interrupted while running " + String.join(" ", command));
        }
    }

    private static void copyOutput(Process process, ByteArrayOutputStream output) {
        try (var input = process.getInputStream()) {
            input.transferTo(output);
        } catch (IOException exception) {
            // The process may close its stream while being forcefully terminated.
        }
    }

    private static void assertCommandSucceeded(CommandResult result) {
        assertEquals(0, result.exitCode(), result.output());
    }

    private static Path findProjectRoot() {
        Path current = Path.of("").toAbsolutePath();
        while (current != null) {
            if (Files.isRegularFile(current.resolve("docker-compose.yml"))) {
                return current;
            }
            current = current.getParent();
        }
        throw new IllegalStateException(
                "Could not find docker-compose.yml from " + Path.of("").toAbsolutePath());
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }

    private record CommandResult(int exitCode, String output) {
    }
}
