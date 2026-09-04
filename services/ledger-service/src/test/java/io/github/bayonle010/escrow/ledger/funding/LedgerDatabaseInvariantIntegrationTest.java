package io.github.bayonle010.escrow.ledger.funding;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
    "ledger.messaging.consumer-enabled=false",
    "ledger.outbox.publisher.enabled=false"
})
class LedgerDatabaseInvariantIntegrationTest {

    @Container
    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:18.6-bookworm")
            .withDatabaseName("ledger_invariant_test")
            .withUsername("ledger_test")
            .withPassword("ledger_test");

    @DynamicPropertySource
    static void configureDataSource(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @BeforeEach
    void clearLedger() {
        jdbcTemplate.execute(
                "TRUNCATE outbox_events, consumer_inbox, ledger_entries, ledger_journals, "
                        + "ledger_account_balances, ledger_accounts");
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

    private int count(String table) {
        return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Integer.class);
    }
}
