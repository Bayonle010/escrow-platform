package io.github.bayonle010.escrow.escrow.acceptance.api;

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

import io.github.bayonle010.escrow.escrow.acceptance.controller.EscrowTermsAcceptanceController;
import io.github.bayonle010.escrow.escrow.acceptance.domain.AcceptedEscrowTerms;
import io.github.bayonle010.escrow.escrow.acceptance.dto.AcceptEscrowTermsRequest;
import io.github.bayonle010.escrow.escrow.acceptance.service.EscrowTermsAcceptanceService;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.escrow.shared.UuidV7Generator;
import io.github.bayonle010.escrow.escrow.shared.exception.ApiExceptionHandler;
import io.github.bayonle010.escrow.escrow.shared.exception.EscrowNotFoundException;

class EscrowTermsAcceptanceControllerTest {

    private static final UUID ESCROW_ID = UUID.fromString("019c0000-0000-7000-8000-000000000020");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");
    private static final UUID ACCEPTANCE_REFERENCE =
            UUID.fromString("019c0000-0000-7000-8000-000000000030");

    private final EscrowTermsAcceptanceService acceptanceService =
            mock(EscrowTermsAcceptanceService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new EscrowTermsAcceptanceController(acceptanceService);
        var correlationFilter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(correlationFilter)
                .build();
    }

    @Test
    void returnsAcceptedTermsInTheStandardEnvelope() throws Exception {
        when(acceptanceService.accept(
                eq(ESCROW_ID),
                any(AcceptEscrowTermsRequest.class),
                any(UUID.class)))
                .thenReturn(new AcceptedEscrowTerms(
                        ESCROW_ID,
                        ACCEPTANCE_REFERENCE,
                        1,
                        SELLER_ID,
                        Instant.parse("2026-08-20T12:00:00Z"),
                        EscrowState.AWAITING_FUNDING));

        mockMvc.perform(post("/api/v1/escrows/{escrowId}/accept-terms", ESCROW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isOk())
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.data.escrowId").value(ESCROW_ID.toString()))
                .andExpect(jsonPath("$.data.acceptanceReference")
                        .value(ACCEPTANCE_REFERENCE.toString()))
                .andExpect(jsonPath("$.data.termsVersion").value(1))
                .andExpect(jsonPath("$.data.acceptedBy").value(SELLER_ID.toString()))
                .andExpect(jsonPath("$.data.state").value("AWAITING_FUNDING"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void rejectsInvalidInputBeforeCallingTheService() throws Exception {
        mockMvc.perform(post("/api/v1/escrows/{escrowId}/accept-terms", ESCROW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"termsVersion":0}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"));

        verifyNoInteractions(acceptanceService);
    }

    @Test
    void returnsNotFoundForAnUnknownEscrow() throws Exception {
        when(acceptanceService.accept(
                eq(ESCROW_ID),
                any(AcceptEscrowTermsRequest.class),
                any(UUID.class)))
                .thenThrow(new EscrowNotFoundException(ESCROW_ID));

        mockMvc.perform(post("/api/v1/escrows/{escrowId}/accept-terms", ESCROW_ID)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("ESCROW_NOT_FOUND"));
    }

    @Test
    void rejectsAMalformedEscrowId() throws Exception {
        mockMvc.perform(post("/api/v1/escrows/not-a-uuid/accept-terms")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("escrowId"));

        verifyNoInteractions(acceptanceService);
    }

    private String validRequest() {
        return """
                {
                  "participantId":"%s",
                  "termsVersion":1
                }
                """.formatted(SELLER_ID);
    }
}
