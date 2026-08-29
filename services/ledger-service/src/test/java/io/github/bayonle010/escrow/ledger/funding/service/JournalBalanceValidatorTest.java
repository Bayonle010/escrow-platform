package io.github.bayonle010.escrow.ledger.funding.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import io.github.bayonle010.escrow.ledger.funding.domain.AccountSide;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerEntry;

class JournalBalanceValidatorTest {

    private final JournalBalanceValidator validator = new JournalBalanceValidator();

    @Test
    void acceptsBalancedDoubleEntryJournal() {
        assertThatCode(() -> validator.validate(List.of(
                entry(AccountSide.DEBIT, 100000, "NGN", 1),
                entry(AccountSide.CREDIT, 100000, "NGN", 2))))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectsAnUnbalancedJournal() {
        assertThatThrownBy(() -> validator.validate(List.of(
                entry(AccountSide.DEBIT, 100000, "NGN", 1),
                entry(AccountSide.CREDIT, 99999, "NGN", 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Journal debits must equal journal credits.");
    }

    @Test
    void rejectsMixedCurrencies() {
        assertThatThrownBy(() -> validator.validate(List.of(
                entry(AccountSide.DEBIT, 100000, "NGN", 1),
                entry(AccountSide.CREDIT, 100000, "USD", 2))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Every journal entry must use the journal currency.");
    }

    @Test
    void rejectsSingleEntryJournal() {
        assertThatThrownBy(() -> validator.validate(List.of(
                entry(AccountSide.DEBIT, 100000, "NGN", 1))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("A journal must contain at least two entries.");
    }

    private LedgerEntry entry(AccountSide side, long amountMinor, String currency, int sequence) {
        return new LedgerEntry(
                UUID.randomUUID(),
                UUID.randomUUID(),
                UUID.randomUUID(),
                side,
                amountMinor,
                currency,
                sequence,
                Instant.parse("2026-08-28T00:00:00Z"));
    }
}
