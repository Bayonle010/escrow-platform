package io.github.bayonle010.escrow.escrow.acceptance.controller;

import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.escrow.acceptance.domain.AcceptedEscrowTerms;
import io.github.bayonle010.escrow.escrow.acceptance.dto.AcceptEscrowTermsRequest;
import io.github.bayonle010.escrow.escrow.acceptance.dto.AcceptedEscrowTermsResponse;
import io.github.bayonle010.escrow.escrow.acceptance.service.EscrowTermsAcceptanceService;
import io.github.bayonle010.escrow.escrow.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.escrow.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/escrows")
@Tag(name = "Escrows", description = "Escrow agreement operations")
public class EscrowTermsAcceptanceController {

    private final EscrowTermsAcceptanceService acceptanceService;

    public EscrowTermsAcceptanceController(EscrowTermsAcceptanceService acceptanceService) {
        this.acceptanceService = acceptanceService;
    }

    @PostMapping("/{escrowId}/accept-terms")
    @Operation(
            summary = "Accept the current escrow terms",
            description = "Records counterparty acceptance, moves the escrow to awaiting funding, "
                    + "and creates an EscrowTermsAccepted outbox event in one transaction.")
    public ResponseEntity<ApiResponse<AcceptedEscrowTermsResponse>> acceptTerms(
            @PathVariable UUID escrowId,
            @Valid @RequestBody AcceptEscrowTermsRequest request,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {

        AcceptedEscrowTerms accepted = acceptanceService.accept(escrowId, request, correlationId);

        return ResponseEntity.ok(new ApiResponse<>(
                AcceptedEscrowTermsResponse.from(accepted),
                correlationId.toString()));
    }
}
