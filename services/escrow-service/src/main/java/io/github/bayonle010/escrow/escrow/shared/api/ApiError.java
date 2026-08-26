package io.github.bayonle010.escrow.escrow.shared.api;

import java.util.List;

public record ApiError(ErrorCode code, String message, String correlationId, List<FieldViolation> details) {

    public record FieldViolation(String field, String message) {
    }
}
