package io.github.bayonle010.escrow.ledger.funding.service;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import io.github.bayonle010.escrow.ledger.funding.domain.AccountBalance;
import io.github.bayonle010.escrow.ledger.funding.repository.LedgerFundingRepository;
import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;
import io.github.bayonle010.escrow.ledger.shared.exception.LedgerApiException;

@Service
public class AccountBalanceService {

    private final LedgerFundingRepository repository;

    public AccountBalanceService(LedgerFundingRepository repository) {
        this.repository = repository;
    }

    public AccountBalance get(UUID accountId) {
        return repository.findBalance(accountId).orElseThrow(() -> new LedgerApiException(
                ErrorCode.LEDGER_ACCOUNT_NOT_FOUND,
                HttpStatus.NOT_FOUND,
                "accountId",
                "Ledger account " + accountId + " was not found."));
    }
}
