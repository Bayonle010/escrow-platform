package io.github.bayonle010.escrow.escrow.query.controller;

import java.util.UUID;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.github.bayonle010.escrow.escrow.query.dto.EscrowDetailsResponse;
import io.github.bayonle010.escrow.escrow.query.service.EscrowQueryService;
import io.github.bayonle010.escrow.escrow.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.escrow.shared.api.ApiResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

@RestController
@RequestMapping("/api/v1/escrows")
@Tag(name = "Escrows", description = "Escrow agreement operations")
public class EscrowQueryController {

    private final EscrowQueryService queryService;

    public EscrowQueryController(EscrowQueryService queryService) {
        this.queryService = queryService;
    }

    @GetMapping("/{escrowId}")
    @Operation(summary = "Get an escrow", description = "Returns the authoritative escrow funding snapshot.")
    public ApiResponse<EscrowDetailsResponse> get(
            @PathVariable UUID escrowId,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
        return new ApiResponse<>(
                EscrowDetailsResponse.from(queryService.get(escrowId)),
                correlationId.toString());
    }
}
