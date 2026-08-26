package io.github.bayonle010.escrow.escrow.creation.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
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
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.bayonle010.escrow.escrow.creation.controller.EscrowCreationController;
import io.github.bayonle010.escrow.escrow.creation.domain.CreatedEscrow;
import io.github.bayonle010.escrow.escrow.creation.domain.EscrowState;
import io.github.bayonle010.escrow.escrow.creation.dto.CreateEscrowRequest;
import io.github.bayonle010.escrow.escrow.creation.service.EscrowCreationService;
import io.github.bayonle010.escrow.escrow.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.escrow.shared.UuidV7Generator;
import io.github.bayonle010.escrow.escrow.shared.exception.ApiExceptionHandler;

class EscrowCreationControllerTest {

    private static final UUID BUYER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000001");
    private static final UUID SELLER_ID = UUID.fromString("019c0000-0000-7000-8000-000000000002");

    private final EscrowCreationService creationService = mock(EscrowCreationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new EscrowCreationController(creationService);
        var correlationFilter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(correlationFilter)
                .build();
    }

    @Test
    void returnsCreatedResourceInTheStandardEnvelope() throws Exception {
        UUID escrowId = UUID.fromString("019c0000-0000-7000-8000-000000000020");
        when(creationService.create(any(CreateEscrowRequest.class), any(UUID.class)))
                .thenReturn(new CreatedEscrow(
                        escrowId,
                        BUYER_ID,
                        SELLER_ID,
                        100000,
                        "NGN",
                        1,
                        EscrowState.AWAITING_COUNTERPARTY,
                        Instant.parse("2026-08-20T12:00:00Z")));

        var mvcResult = mockMvc.perform(post("/api/v1/escrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validRequest()))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/escrows/" + escrowId))
                .andExpect(header().exists(CorrelationIdFilter.HEADER_NAME))
                .andExpect(jsonPath("$.data.id").value(escrowId.toString()))
                .andExpect(jsonPath("$.data.state").value("AWAITING_COUNTERPARTY"))
                .andExpect(jsonPath("$.data.termsVersion").value(1))
                .andExpect(jsonPath("$.correlationId").isNotEmpty())
                .andReturn();

        ArgumentCaptor<UUID> correlationId = ArgumentCaptor.forClass(UUID.class);
        verify(creationService).create(any(CreateEscrowRequest.class), correlationId.capture());
        String responseCorrelationId = mvcResult.getResponse().getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(correlationId.getValue().toString()).isEqualTo(responseCorrelationId);
    }

    @Test
    void rejectsInvalidInputBeforeCallingTheApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/escrows")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "buyerId":"%s",
                                  "sellerId":"%s",
                                  "createdBy":"%s",
                                  "amountMinor":0,
                                  "currency":"N",
                                  "description":"",
                                  "category":"",
                                  "deliveryDeadline":"2020-01-01T00:00:00Z",
                                  "inspectionPeriodDays":0,
                                  "releaseConditions":"",
                                  "refundConditions":""
                                }
                                """.formatted(BUYER_ID, SELLER_ID, BUYER_ID)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isNotEmpty());

        verifyNoInteractions(creationService);
    }

    private String validRequest() {
        return """
                {
                  "buyerId":"%s",
                  "sellerId":"%s",
                  "createdBy":"%s",
                  "amountMinor":100000,
                  "currency":"NGN",
                  "description":"Professional camera",
                  "category":"goods",
                  "deliveryDeadline":"2099-09-30T12:00:00Z",
                  "inspectionPeriodDays":7,
                  "releaseConditions":"Release after accepted delivery",
                  "refundConditions":"Refund if delivery misses the deadline"
                }
                """.formatted(BUYER_ID, SELLER_ID, BUYER_ID);
    }
}
