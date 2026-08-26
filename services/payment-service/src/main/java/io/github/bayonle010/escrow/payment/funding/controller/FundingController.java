package io.github.bayonle010.escrow.payment.funding.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.payment.funding.domain.InitiatedPayment;
import io.github.bayonle010.escrow.payment.funding.dto.InitiateFundingRequest;
import io.github.bayonle010.escrow.payment.funding.dto.InitiatedPaymentResponse;
import io.github.bayonle010.escrow.payment.funding.service.FundingInitiationService;
import io.github.bayonle010.escrow.payment.funding.service.PaymentQueryService;
import io.github.bayonle010.escrow.payment.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.payment.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Funding", description = "Escrow funding and payment instruction operations")
public class FundingController {

    public static final String IDEMPOTENCY_HEADER = "Idempotency-Key";

    private final FundingInitiationService initiationService;
    private final PaymentQueryService queryService;

    public FundingController(
            FundingInitiationService initiationService,
            PaymentQueryService queryService) {
        this.initiationService = initiationService;
        this.queryService = queryService;
    }

    @PostMapping("/escrows/{escrowId}/fund")
    @Operation(
            summary = "Initiate escrow funding",
            description = "Creates one durable, idempotent payment instruction with status PROCESSING.")
    public ResponseEntity<ApiResponse<InitiatedPaymentResponse>> initiate(
            @PathVariable UUID escrowId,
            @RequestHeader(name = IDEMPOTENCY_HEADER, required = false) String idempotencyKey,
            @Valid @RequestBody InitiateFundingRequest request,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
         InitiatedPayment payment = initiationService.initiate(
                escrowId,
                request.payerId(),
                idempotencyKey,
                correlationId);
        return ResponseEntity.accepted()
                .location(URI.create("/api/v1/payments/" + payment.paymentId()))
                .body(new ApiResponse<>(
                        InitiatedPaymentResponse.from(payment),
                        correlationId.toString()));
    }

    @GetMapping("/payments/{paymentId}")
    @Operation(summary = "Get a payment", description = "Returns the current authoritative payment status.")
    public ApiResponse<InitiatedPaymentResponse> get(
            @PathVariable UUID paymentId,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
        return new ApiResponse<>(
                InitiatedPaymentResponse.from(queryService.get(paymentId)),
                correlationId.toString());
    }
}
