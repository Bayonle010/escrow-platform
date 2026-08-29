package io.github.bayonle010.escrow.ledger.funding.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.ledger.funding.dto.AccountBalanceResponse;
import io.github.bayonle010.escrow.ledger.funding.dto.FundingSecuredResponse;
import io.github.bayonle010.escrow.ledger.funding.dto.PaymentSucceededEventRequest;
import io.github.bayonle010.escrow.ledger.funding.service.AccountBalanceService;
import io.github.bayonle010.escrow.ledger.funding.service.InternalEventAuthenticator;
import io.github.bayonle010.escrow.ledger.funding.service.LedgerFundingService;
import io.github.bayonle010.escrow.ledger.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.ledger.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/internal/v1/ledger")
@Tag(name = "Ledger Funding", description = "Internal double-entry funding operations")
public class LedgerFundingController {

    public static final String INTERNAL_SECRET_HEADER = "X-Internal-Event-Secret";

    private final InternalEventAuthenticator authenticator;
    private final LedgerFundingService fundingService;
    private final AccountBalanceService balanceService;

    public LedgerFundingController(
            InternalEventAuthenticator authenticator,
            LedgerFundingService fundingService,
            AccountBalanceService balanceService) {
        this.authenticator = authenticator;
        this.fundingService = fundingService;
        this.balanceService = balanceService;
    }

    @PostMapping("/events/payment-succeeded")
    @Operation(
            summary = "Process PaymentSucceeded",
            description = "Temporary authenticated event boundary used before the Kafka consumer is introduced. "
                    + "Posts one balanced funding journal and an EscrowFundingSecured outbox event.")
    public ApiResponse<FundingSecuredResponse> secureFunding(
            @RequestHeader(name = INTERNAL_SECRET_HEADER, required = false) String internalSecret,
            @Valid @RequestBody PaymentSucceededEventRequest request) {
        authenticator.authenticate(internalSecret);
        var secured = fundingService.secure(request.toDomain());
        return new ApiResponse<>(FundingSecuredResponse.from(secured), request.correlationId().toString());
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
