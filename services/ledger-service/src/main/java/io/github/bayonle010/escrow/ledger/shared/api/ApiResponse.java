package io.github.bayonle010.escrow.ledger.shared.api;

public record ApiResponse<T>(T data, String correlationId) {
}
