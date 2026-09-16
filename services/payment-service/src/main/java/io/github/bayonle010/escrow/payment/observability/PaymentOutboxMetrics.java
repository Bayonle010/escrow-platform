package io.github.bayonle010.escrow.payment.observability;

import java.math.BigDecimal;
import java.util.List;
import java.util.Locale;

import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;

@Component
public class PaymentOutboxMetrics {

    private static final List<String> STATUSES = List.of("PENDING", "PUBLISHED", "FAILED");

    public PaymentOutboxMetrics(MeterRegistry registry, JdbcTemplate jdbcTemplate) {
        for (String status : STATUSES) {
            Gauge.builder(
                            "escrow.outbox.events",
                            jdbcTemplate,
                            template -> countEvents(template, status))
                    .description("Number of payment outbox events by status")
                    .tag("status", status.toLowerCase(Locale.ROOT))
                    .register(registry);
        }

        Gauge.builder(
                        "escrow.outbox.oldest.pending.age",
                        jdbcTemplate,
                        PaymentOutboxMetrics::oldestPendingAgeSeconds)
                .baseUnit("seconds")
                .description("Age of the oldest pending payment outbox event")
                .register(registry);

        Gauge.builder(
                        "escrow.outbox.pending.max.attempts",
                        jdbcTemplate,
                        PaymentOutboxMetrics::maximumPendingAttempts)
                .description("Highest publication-attempt count among pending payment outbox events")
                .register(registry);
    }

    private static double countEvents(JdbcTemplate jdbcTemplate, String status) {
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
}
