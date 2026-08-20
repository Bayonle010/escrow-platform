package io.github.bayonle010.escrow.identity.shared.api;

public record ApiResponse<T>(T data, String correlationId) {
}
