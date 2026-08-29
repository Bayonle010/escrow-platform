package io.github.bayonle010.escrow.ledger.shared.exception;

import org.springframework.http.HttpStatus;

import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;

public final class LedgerApiException extends RuntimeException {

    private final ErrorCode errorCode;
    private final HttpStatus status;
    private final String field;

    public LedgerApiException(ErrorCode errorCode, HttpStatus status, String field, String message) {
        super(message);
        this.errorCode = errorCode;
        this.status = status;
        this.field = field;
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public HttpStatus getStatus() {
        return status;
    }

    public String getField() {
        return field;
    }
}
