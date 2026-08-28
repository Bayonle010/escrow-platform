package io.github.bayonle010.escrow.payment.funding.api;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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

import io.github.bayonle010.escrow.payment.funding.controller.SimulatedProviderCallbackController;
import io.github.bayonle010.escrow.payment.funding.domain.ConfirmedPayment;
import io.github.bayonle010.escrow.payment.funding.domain.PaymentStatus;
import io.github.bayonle010.escrow.payment.funding.service.PaymentConfirmationService;
import io.github.bayonle010.escrow.payment.funding.service.SimulatedProviderCallbackAuthenticator;
import io.github.bayonle010.escrow.payment.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.payment.shared.UuidV7Generator;
import io.github.bayonle010.escrow.payment.shared.api.ErrorCode;
import io.github.bayonle010.escrow.payment.shared.exception.ApiExceptionHandler;
import io.github.bayonle010.escrow.payment.shared.exception.PaymentApiException;

class SimulatedProviderCallbackControllerTest {

    private static final UUID PAYMENT_ID = UUID.fromString("019c0000-0000-7000-8000-000000000030");
    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final String CALLBACK_SECRET = "callback-secret";
    private static final String PROVIDER_REFERENCE = "simulated-transaction-1001";

    private final SimulatedProviderCallbackAuthenticator authenticator =
            mock(SimulatedProviderCallbackAuthenticator.class);
    private final PaymentConfirmationService confirmationService = mock(PaymentConfirmationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new SimulatedProviderCallbackController(authenticator, confirmationService);
        var filter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(filter)
                .build();
    }

    @Test
    void authenticatesAndConfirmsThePayment() throws Exception {
        when(confirmationService.confirm(eq(PAYMENT_ID), eq(PROVIDER_REFERENCE), any(UUID.class)))
                .thenReturn(new ConfirmedPayment(
                        PAYMENT_ID,
                        ESCROW_ID,
                        BUYER_ID,
                        100000,
                        "NGN",
                        "SIMULATED",
                        PROVIDER_REFERENCE,
                        PaymentStatus.SUCCEEDED,
                        Instant.parse("2026-08-28T00:00:00Z"),
                        false));

        mockMvc.perform(post(
                        "/api/v1/providers/simulated/payments/{paymentId}/confirm",
                        PAYMENT_ID)
                        .header(
                                SimulatedProviderCallbackController.CALLBACK_SECRET_HEADER,
                                CALLBACK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerReference\":\"" + PROVIDER_REFERENCE + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(PAYMENT_ID.toString()))
                .andExpect(jsonPath("$.data.status").value("SUCCEEDED"))
                .andExpect(jsonPath("$.data.providerReference").value(PROVIDER_REFERENCE))
                .andExpect(jsonPath("$.data.replayed").value(false));

        verify(authenticator).authenticate(CALLBACK_SECRET);
    }

    @Test
    void rejectsAnUnauthenticatedCallback() throws Exception {
        doThrow(new PaymentApiException(
                        ErrorCode.PROVIDER_CALLBACK_UNAUTHORIZED,
                        HttpStatus.UNAUTHORIZED,
                        null,
                        "The simulated provider callback could not be authenticated."))
                .when(authenticator).authenticate(null);

        mockMvc.perform(post(
                        "/api/v1/providers/simulated/payments/{paymentId}/confirm",
                        PAYMENT_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerReference\":\"" + PROVIDER_REFERENCE + "\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value("PROVIDER_CALLBACK_UNAUTHORIZED"));

        verifyNoInteractions(confirmationService);
    }

    @Test
    void rejectsAnInvalidRequestBeforeConfirming() throws Exception {
        mockMvc.perform(post(
                        "/api/v1/providers/simulated/payments/{paymentId}/confirm",
                        PAYMENT_ID)
                        .header(
                                SimulatedProviderCallbackController.CALLBACK_SECRET_HEADER,
                                CALLBACK_SECRET)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"providerReference\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

        verifyNoInteractions(authenticator, confirmationService);
    }
}
