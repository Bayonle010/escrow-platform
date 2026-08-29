package io.github.bayonle010.escrow.ledger.funding.service;

import java.util.List;

import org.springframework.stereotype.Component;

import io.github.bayonle010.escrow.ledger.funding.domain.AccountSide;
import io.github.bayonle010.escrow.ledger.funding.domain.LedgerEntry;

@Component
public class JournalBalanceValidator {

    public void validate(List<LedgerEntry> entries) {
        if (entries.size() < 2) {
            throw new IllegalArgumentException("A journal must contain at least two entries.");
        }
        String currency = entries.getFirst().currency();
        long debits = 0;
        long credits = 0;
        for (LedgerEntry entry : entries) {
            if (entry.amountMinor() <= 0) {
                throw new IllegalArgumentException("Ledger entry amounts must be positive.");
            }
            if (!currency.equals(entry.currency())) {
                throw new IllegalArgumentException("Every journal entry must use the journal currency.");
            }
            if (entry.direction() == AccountSide.DEBIT) {
                debits = Math.addExact(debits, entry.amountMinor());
            } else {
                credits = Math.addExact(credits, entry.amountMinor());
            }
        }
        if (debits != credits) {
            throw new IllegalArgumentException("Journal debits must equal journal credits.");
        }
    }
}
