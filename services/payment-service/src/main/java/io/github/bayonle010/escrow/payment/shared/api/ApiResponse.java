package io.github.bayonle010.escrow.payment.shared.api;

public record ApiResponse<T>(T data, String correlationId) {
}
