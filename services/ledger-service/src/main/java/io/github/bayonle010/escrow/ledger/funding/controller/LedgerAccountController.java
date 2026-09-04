package io.github.bayonle010.escrow.ledger.funding.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.ledger.funding.dto.AccountBalanceResponse;
import io.github.bayonle010.escrow.ledger.funding.service.AccountBalanceService;
import io.github.bayonle010.escrow.ledger.funding.service.InternalEventAuthenticator;
import io.github.bayonle010.escrow.ledger.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.ledger.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/internal/v1/ledger")
@Tag(name = "Ledger Accounts", description = "Internal ledger account operations")
public class LedgerAccountController {

    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Event-Secret";

    private final InternalEventAuthenticator authenticator;
    private final AccountBalanceService balanceService;

    public LedgerAccountController(
            InternalEventAuthenticator authenticator,
            AccountBalanceService balanceService) {
        this.authenticator = authenticator;
        this.balanceService = balanceService;
    }

    @GetMapping("/accounts/{accountId}/balance")
    @Operation(summary = "Get a materialized ledger account balance")
    public ApiResponse<AccountBalanceResponse> getBalance(
            @PathVariable UUID accountId,
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
        authenticator.authenticate(internalSecret);
        return new ApiResponse<>(
                AccountBalanceResponse.from(balanceService.get(accountId)),
                correlationId.toString());
    }
}
