package io.github.bayonle010.escrow.escrow.shared.exception;

import io.github.bayonle010.escrow.escrow.shared.api.ErrorCode;

public class InvalidEscrowException extends RuntimeException {

    private final ErrorCode errorCode;
    private final String field;

    public InvalidEscrowException(ErrorCode errorCode, String field, String message) {
        super(message);
        this.errorCode = errorCode;
        this.field = field;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public String getField() {
        return field;
    }
}
