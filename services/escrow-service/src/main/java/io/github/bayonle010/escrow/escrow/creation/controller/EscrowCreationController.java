package io.github.bayonle010.escrow.escrow.creation.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.escrow.creation.domain.CreatedEscrow;
import io.github.bayonle010.escrow.escrow.creation.dto.CreateEscrowRequest;
import io.github.bayonle010.escrow.escrow.creation.dto.CreatedEscrowResponse;
import io.github.bayonle010.escrow.escrow.creation.service.EscrowCreationService;
import io.github.bayonle010.escrow.escrow.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.escrow.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/escrows")
@Tag(name = "Escrows", description = "Escrow agreement operations")
public class EscrowCreationController {

    private final EscrowCreationService creationService;

    public EscrowCreationController(EscrowCreationService creationService) {
        this.creationService = creationService;
    }

    @PostMapping
    @Operation(
            summary = "Create an escrow",
            description = "Creates an escrow, terms version 1, and EscrowCreated outbox event in one transaction.")
    public ResponseEntity<ApiResponse<CreatedEscrowResponse>> create(
            @Valid @RequestBody CreateEscrowRequest request,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
        CreatedEscrow createdEscrow = creationService.create(request, correlationId);
        CreatedEscrowResponse response = CreatedEscrowResponse.from(createdEscrow);

        return ResponseEntity
                .created(URI.create("/api/v1/escrows/" + createdEscrow.escrowId()))
                .body(new ApiResponse<>(response, correlationId.toString()));
    }
}
