package io.github.bayonle010.escrow.ledger.observability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class LedgerSafetyMetrics {

    private static final List<String> OUTBOX_STATUSES = List.of("PENDING", "PUBLISHED", "FAILED");

    public LedgerSafetyMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        for (String status : OUTBOX_STATUSES) {
            Gauge.builder(
                            "escrow.outbox.events",
                            jdbcTemplate,
                            template -> countOutboxEvents(template, status))
                    .description("Number of ledger outbox events by status")
                    .tag("status", status.toLowerCase(Locale.ROOT))
                    .register(registry);
        }

        Gauge.builder(
                        "escrow.outbox.oldest.pending.age",
                        jdbcTemplate,
                        LedgerSafetyMetrics::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .description("Age of the oldest pending ledger outbox event")
                .register(registry);

        Gauge.builder(
                        "escrow.outbox.pending.max.attempts",
                        jdbcTemplate,
                        LedgerSafetyMetrics::maximumPendingAttempts)
                .description("Highest publication-attempt count among pending ledger outbox events")
                .register(registry);

        Gauge.builder(
                        "escrow.ledger.imbalanced.journals",
                        jdbcTemplate,
                        LedgerSafetyMetrics::imbalancedJournalCount)
                .description("Number of persisted journals whose debit and credit totals differ")
                .register(registry);
    }

    private static double countOutboxEvents(JdbcTemplate jdbcTemplate, String status) {
        try {
            Long count = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM outbox_events WHERE status = ?",
                    Long.class,
                    status);
            return count == null ? Double.NaN : count.doubleValue();
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }

    private static double oldestPendingAgeSeconds(JdbcTemplate jdbcTemplate) {
        try {
            BigDecimal age = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(
                        EXTRACT(EPOCH FROM (CURRENT_TIMESTAMP - MIN(occurred_at))),
                        0
                    )
                    FROM outbox_events
                    WHERE status = 'PENDING'
                    """, BigDecimal.class);
            return age == null ? Double.NaN : age.doubleValue();
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }

    private static double maximumPendingAttempts(JdbcTemplate jdbcTemplate) {
        try {
            Integer attempts = jdbcTemplate.queryForObject("""
                    SELECT COALESCE(MAX(attempts), 0)
                    FROM outbox_events
                    WHERE status = 'PENDING'
                    """, Integer.class);
            return attempts == null ? Double.NaN : attempts.doubleValue();
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }

    private static double imbalancedJournalCount(JdbcTemplate jdbcTemplate) {
        try {
            Long count = jdbcTemplate.queryForObject("""
                    SELECT COUNT(*)
                    FROM (
                        SELECT journal_id
                        FROM ledger_entries
                        GROUP BY journal_id
                        HAVING SUM(CASE
                            WHEN direction = 'DEBIT' THEN amount_minor
                            ELSE -amount_minor
                        END) <> 0
                    ) imbalanced_journals
                    """, Long.class);
            return count == null ? Double.NaN : count.doubleValue();
        } catch (DataAccessException exception) {
            return Double.NaN;
        }
    }
}
