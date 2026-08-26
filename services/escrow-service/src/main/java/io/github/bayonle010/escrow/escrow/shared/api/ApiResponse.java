package io.github.bayonle010.escrow.escrow.shared.api;

public record ApiResponse<T>(T data, String correlationId) {
}
