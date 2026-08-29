package io.github.bayonle010.escrow.ledger.funding.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.bayonle010.escrow.ledger.funding.controller.LedgerFundingController;
import io.github.bayonle010.escrow.ledger.funding.domain.FundingSecured;
import io.github.bayonle010.escrow.ledger.funding.domain.PaymentSucceededEvent;
import io.github.bayonle010.escrow.ledger.funding.service.AccountBalanceService;
import io.github.bayonle010.escrow.ledger.funding.service.InternalEventAuthenticator;
import io.github.bayonle010.escrow.ledger.funding.service.LedgerFundingService;
import io.github.bayonle010.escrow.ledger.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.ledger.shared.UuidV7Generator;
import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;
import io.github.bayonle010.escrow.ledger.shared.exception.ApiExceptionHandler;
import io.github.bayonle010.escrow.ledger.shared.exception.LedgerApiException;

class LedgerFundingControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID JOURNAL_ID = UUID.fromString("019c0000-0000-7000-8000-000000000050");
    private static final UUID PROVIDER_ACCOUNT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000051");
    private static final UUID ESCROW_ACCOUNT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000052");
    private static final String SECRET = "internal-secret";

    private final InternalEventAuthenticator authenticator = mock(InternalEventAuthenticator.class);
    private final LedgerFundingService fundingService = mock(LedgerFundingService.class);
    private final AccountBalanceService balanceService = mock(AccountBalanceService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new LedgerFundingController(authenticator, fundingService, balanceService);
        var filter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(filter)
                .build();
    }

    @Test
    void authenticatesAndPostsFunding() throws Exception {
        when(fundingService.secure(any(PaymentSucceededEvent.class)))
                .thenReturn(new FundingSecured(
                        JOURNAL_ID,
                        PAYMENT_ID,
                        ESCROW_ID,
                        100000,
                        "NGN",
                        PROVIDER_ACCOUNT_ID,
                        ESCROW_ACCOUNT_ID,
                        Instant.parse("2026-08-28T00:00:00Z"),
                        false));

        mockMvc.perform(post("/internal/v1/ledger/events/payment-succeeded")
                        .header(LedgerFundingController.INTERNAL_SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.journalId").value(JOURNAL_ID.toString()))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.amountMinor").value(100000))
                .andExpect(jsonPath("$.data.replayed").value(false));

        verify(authenticator).authenticate(SECRET);
    }

    @Test
    void rejectsAnUnauthenticatedEvent() throws Exception {
        doThrow(new LedgerApiException(
                        ErrorCode.INTERNAL_EVENT_UNAUTHORIZED,
                        HttpStatus.UNAUTHORIZED,
                        null,
                        "The internal event request could not be authenticated."))
                .when(authenticator).authenticate(null);

        mockMvc.perform(post("/internal/v1/ledger/events/payment-succeeded")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("INTERNAL_EVENT_UNAUTHORIZED"));

        verifyNoInteractions(fundingService);
    }

    @Test
    void rejectsAnInvalidAmountBeforePosting() throws Exception {
        mockMvc.perform(post("/internal/v1/ledger/events/payment-succeeded")
                        .header(LedgerFundingController.INTERNAL_SECRET_HEADER, SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest().replace("\"amountMinor\":100000", "\"amountMinor\":0")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

        verifyNoInteractions(authenticator, fundingService);
    }

    private String validRequest() {
        return """
                {
                  "eventId":"019c0000-0000-7000-8000-000000000040",
                  "eventVersion":1,
                  "occurredAt":"2026-08-28T00:00:00Z",
                  "paymentId":"019c0000-0000-7000-8000-000000000030",
                  "escrowId":"019c0000-0000-7000-8000-000000000020",
                  "payerId":"019c0000-0000-7000-8000-000000000001",
                  "amountMinor":100000,
                  "currency":"NGN",
                  "provider":"SIMULATED",
                  "providerReference":"provider-reference",
                  "aggregateVersion":1,
                  "correlationId":"019c0000-0000-7000-8000-000000000010"
                }
                """;
    }
}
