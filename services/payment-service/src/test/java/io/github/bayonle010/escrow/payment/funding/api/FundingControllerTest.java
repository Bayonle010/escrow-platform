package io.github.bayonle010.escrow.payment.funding.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.bayonle010.escrow.payment.funding.controller.FundingController;
import io.github.bayonle010.escrow.payment.funding.domain.InitiatedPayment;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.service.FundingInitiationService;
import io.github.bayonle010.escrow.payment.funding.service.PaymentQueryService;
import io.github.bayonle010.escrow.payment.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.payment.shared.UuidV7Generator;
import io.github.bayonle010.escrow.payment.shared.exception.ApiExceptionHandler;

class FundingControllerTest {

    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final String IDEMPOTENCY_KEY = "8e03978e-40d5-43e8-bc93-6894a57f9324";

    private final FundingInitiationService initiationService = mock(FundingInitiationService.class);
    private final PaymentQueryService queryService = mock(PaymentQueryService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new FundingController(initiationService, queryService);
        var filter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(filter)
                .build();
    }

    @Test
    void returnsTheAsynchronousPaymentInstruction() throws Exception {
        when(initiationService.initiate(
                eq(ESCROW_ID), eq(BUYER_ID), eq(IDEMPOTENCY_KEY), any(UUID.class)))
                .thenReturn(new InitiatedPayment(
                        PAYMENT_ID,
                        ESCROW_ID,
                        BUYER_ID,
                        100000,
                        "NGN",
                        "SIMULATED",
                        PaymentStatus.PROCESSING,
                        Instant.parse("2026-08-20T12:00:00Z"),
                        false));

        mockMvc.perform(post("/api/v1/escrows/{escrowId}/fund", ESCROW_ID)
                        .header(FundingController.IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"payerId\":\"" + BUYER_ID + "\"}"))
                .andExpect(status().isAccepted())
                .andExpect(header().string("Location", "/api/v1/payments/" + PAYMENT_ID))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.data.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("PROCESSING"))
                .andExpect(jsonPath("$.data.replayed").value(false))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void rejectsAnInvalidRequestBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/escrows/{escrowId}/fund", ESCROW_ID)
                        .header(FundingController.IDEMPOTENCY_HEADER, IDEMPOTENCY_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

        verifyNoInteractions(initiationService);
    }
}
