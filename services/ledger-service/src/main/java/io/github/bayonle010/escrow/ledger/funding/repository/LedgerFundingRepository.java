package io.github.bayonle010.escrow.ledger.funding.repository;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import io.github.bayonle010.escrow.ledger.funding.domain.AccountBalance;
import io.github.bayonle010.escrow.ledger.funding.domain.AccountSide;
import io.github.bayonle010.escrow.ledger.funding.domain.InboxRecord;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerAccount;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerEntry;
import io.github.bayonle010.escrow.ledger.funding.domain.OutboxEvent;
import io.github.bayonle010.escrow.ledger.funding.domain.PostedFunding;

@Repository
public class LedgerFundingRepository {

    private final JdbcTemplate jdbcTemplate;

    public LedgerFundingRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public boolean claimEvent(
            String consumerName,
            UUID eventId,
            UUID aggregateId,
            String eventType,
            Instant processedAt) {
        return jdbcTemplate.update(
                """
                INSERT INTO consumer_inbox (
                    consumer_name, event_id, aggregate_id, event_type, processed_at
                ) VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (consumer_name, event_id) DO NOTHING
                """,
                consumerName,
                eventId,
                aggregateId,
                eventType,
                databaseTimestamp(processedAt)) == 1;
    }

    public Optional<InboxRecord> findInbox(String consumerName, UUID eventId) {
        return jdbcTemplate.query(
                """
                SELECT aggregate_id, event_type
                FROM consumer_inbox
                WHERE consumer_name = ? AND event_id = ?
                """,
                (resultSet, rowNumber) -> new InboxRecord(
                        resultSet.getObject("aggregate_id", UUID.class),
                        resultSet.getString("event_type")),
                consumerName,
                eventId).stream().findFirst();
    }

    public boolean insertFundingJournal(
            UUID journalId,
            String businessReference,
            UUID paymentId,
            UUID escrowId,
            long amountMinor,
            String currency,
            String provider,
            String providerReference,
            UUID correlationId,
            UUID causationId,
            Instant createdAt) {
        return jdbcTemplate.update(
                """
                INSERT INTO ledger_journals (
                    journal_id, business_reference, journal_type, payment_id, escrow_id,
                    amount_minor, currency, provider, provider_reference,
                    correlation_id, causation_id, created_at
                ) VALUES (?, ?, 'ESCROW_FUNDING', ?, ?, ?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT DO NOTHING
                """,
                journalId,
                businessReference,
                paymentId,
                escrowId,
                amountMinor,
                currency,
                provider,
                providerReference,
                correlationId,
                causationId,
                databaseTimestamp(createdAt)) == 1;
    }

    public LedgerAccount getOrCreateAccount(
            UUID proposedAccountId,
            String ownerType,
            String ownerReference,
            String accountType,
            AccountSide normalSide,
            String currency,
            Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_accounts (
                    account_id, owner_type, owner_reference, account_type,
                    normal_side, currency, status, created_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', ?, 0)
                ON CONFLICT (owner_type, owner_reference, account_type, currency) DO NOTHING
                """,
                proposedAccountId,
                ownerType,
                ownerReference,
                accountType,
                normalSide.name(),
                currency,
                databaseTimestamp(createdAt));
        return jdbcTemplate.queryForObject(
                """
                SELECT account_id, owner_type, owner_reference, account_type,
                       normal_side, currency, status
                FROM ledger_accounts
                WHERE owner_type = ? AND owner_reference = ? AND account_type = ? AND currency = ?
                """,
                (resultSet, rowNumber) -> new LedgerAccount(
                        resultSet.getObject("account_id", UUID.class),
                        resultSet.getString("owner_type"),
                        resultSet.getString("owner_reference"),
                        resultSet.getString("account_type"),
                        AccountSide.valueOf(resultSet.getString("normal_side")),
                        resultSet.getString("currency"),
                        resultSet.getString("status")),
                ownerType,
                ownerReference,
                accountType,
                currency);
    }

    public void ensureBalance(UUID accountId, Instant createdAt) {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_account_balances (
                    account_id, posted_balance_minor, available_balance_minor, version, updated_at
                ) VALUES (?, 0, 0, 0, ?)
                ON CONFLICT (account_id) DO NOTHING
                """,
                accountId,
                databaseTimestamp(createdAt));
    }

    public void lockBalances(UUID firstAccountId, UUID secondAccountId) {
        jdbcTemplate.queryForList(
                """
                SELECT account_id
                FROM ledger_account_balances
                WHERE account_id IN (?, ?)
                ORDER BY account_id
                FOR UPDATE
                """,
                UUID.class,
                firstAccountId,
                secondAccountId);
    }

    public void insertEntry(LedgerEntry entry) {
        jdbcTemplate.update(
                """
                INSERT INTO ledger_entries (
                    entry_id, journal_id, account_id, direction,
                    amount_minor, currency, sequence, created_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                """,
                entry.entryId(),
                entry.journalId(),
                entry.accountId(),
                entry.direction().name(),
                entry.amountMinor(),
                entry.currency(),
                entry.sequence(),
                databaseTimestamp(entry.createdAt()));
    }

    public void increaseNormalBalance(UUID accountId, long amountMinor, Instant updatedAt) {
        int updated = jdbcTemplate.update(
                """
                UPDATE ledger_account_balances
                SET posted_balance_minor = posted_balance_minor + ?,
                    available_balance_minor = available_balance_minor + ?,
                    version = version + 1,
                    updated_at = ?
                WHERE account_id = ?
                """,
                amountMinor,
                amountMinor,
                databaseTimestamp(updatedAt),
                accountId);
        if (updated != 1) {
            throw new IllegalStateException("Expected exactly one ledger balance row to be updated.");
        }
    }

    public void insertOutboxEvent(OutboxEvent event) {
        jdbcTemplate.update(
                """
                INSERT INTO outbox_events (
                    event_id, aggregate_id, aggregate_type, event_type, event_version,
                    partition_key, correlation_id, causation_id, payload, occurred_at,
                    status, attempts, next_attempt_at
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 'PENDING', 0, ?)
                """,
                event.eventId(),
                event.aggregateId(),
                event.aggregateType(),
                event.eventType(),
                event.eventVersion(),
                event.partitionKey(),
                event.correlationId(),
                event.causationId(),
                event.payload().toString(),
                databaseTimestamp(event.occurredAt()),
                databaseTimestamp(event.occurredAt()));
    }

    public Optional<PostedFunding> findFunding(UUID paymentId) {
        return jdbcTemplate.query(
                """
                SELECT j.journal_id, j.payment_id, j.escrow_id, j.amount_minor, j.currency,
                       j.provider, j.provider_reference, j.created_at,
                       (SELECT e.account_id FROM ledger_entries e
                        WHERE e.journal_id = j.journal_id AND e.direction = 'DEBIT'
                        ORDER BY e.sequence LIMIT 1) AS provider_clearing_account_id,
                       (SELECT e.account_id FROM ledger_entries e
                        WHERE e.journal_id = j.journal_id AND e.direction = 'CREDIT'
                        ORDER BY e.sequence LIMIT 1) AS escrow_held_account_id
                FROM ledger_journals j
                WHERE j.payment_id = ?
                """,
                (resultSet, rowNumber) -> mapPostedFunding(resultSet),
                paymentId).stream().findFirst();
    }

    public Optional<AccountBalance> findBalance(UUID accountId) {
        return jdbcTemplate.query(
                """
                SELECT a.account_id, a.owner_type, a.owner_reference, a.account_type, a.currency,
                       b.posted_balance_minor, b.available_balance_minor, b.version, b.updated_at
                FROM ledger_accounts a
                JOIN ledger_account_balances b ON b.account_id = a.account_id
                WHERE a.account_id = ?
                """,
                (resultSet, rowNumber) -> new AccountBalance(
                        resultSet.getObject("account_id", UUID.class),
                        resultSet.getString("owner_type"),
                        resultSet.getString("owner_reference"),
                        resultSet.getString("account_type"),
                        resultSet.getString("currency"),
                        resultSet.getLong("posted_balance_minor"),
                        resultSet.getLong("available_balance_minor"),
                        resultSet.getLong("version"),
                        resultSet.getTimestamp("updated_at").toInstant()),
                accountId).stream().findFirst();
    }

    private PostedFunding mapPostedFunding(ResultSet resultSet) throws SQLException {
        return new PostedFunding(
                resultSet.getObject("journal_id", UUID.class),
                resultSet.getObject("payment_id", UUID.class),
                resultSet.getObject("escrow_id", UUID.class),
                resultSet.getLong("amount_minor"),
                resultSet.getString("currency"),
                resultSet.getString("provider"),
                resultSet.getString("provider_reference"),
                resultSet.getObject("provider_clearing_account_id", UUID.class),
                resultSet.getObject("escrow_held_account_id", UUID.class),
                resultSet.getTimestamp("created_at").toInstant());
    }

    private static OffsetDateTime databaseTimestamp(Instant instant) {
        return instant.atOffset(ZoneOffset.UTC);
    }
}
