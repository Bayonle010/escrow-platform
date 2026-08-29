package io.github.bayonle010.escrow.ledger.shared.exception;

import java.util.List;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.github.bayonle010.escrow.ledger.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.ledger.shared.api.ApiError;
import io.github.bayonle010.escrow.ledger.shared.api.ErrorCode;

@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger LOGGER = LoggerFactory.getLogger(ApiExceptionHandler.class);

    @ExceptionHandler(MethodArgumentNotValidException.class)
    ResponseEntity<ApiError> handleValidation(
            MethodArgumentNotValidException exception,
            HttpServletRequest request) {
        List<ApiError.FieldViolation> details = exception.getBindingResult().getFieldErrors().stream()
                .map(error -> new ApiError.FieldViolation(error.getField(), error.getDefaultMessage()))
                .toList();
        return ResponseEntity.badRequest().body(new ApiError(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                "The request contains invalid fields.",
                correlationId(request),
                details));
    }

    @ExceptionHandler(LedgerApiException.class)
    ResponseEntity<ApiError> handleLedgerApiException(
            LedgerApiException exception,
            HttpServletRequest request) {
        List<ApiError.FieldViolation> details = exception.getField() == null
                ? List.of()
                : List.of(new ApiError.FieldViolation(exception.getField(), exception.getMessage()));
        return ResponseEntity.status(exception.getStatus()).body(new ApiError(
                exception.getErrorCode(),
                exception.getMessage(),
                correlationId(request),
                details));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                ErrorCode.REQUEST_BODY_INVALID,
                "The request body is missing or malformed.",
                correlationId(request),
                List.of()));
    }

    @ExceptionHandler(MethodArgumentTypeMismatchException.class)
    ResponseEntity<ApiError> handleTypeMismatch(
            MethodArgumentTypeMismatchException exception,
            HttpServletRequest request) {
        return ResponseEntity.badRequest().body(new ApiError(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                "The request contains invalid fields.",
                correlationId(request),
                List.of(new ApiError.FieldViolation(
                        exception.getName(),
                        "The value has an invalid format."))));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOGGER.error("Unhandled request failure correlationId={}", correlationId, exception);
        return ResponseEntity.internalServerError().body(new ApiError(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "The request could not be completed.",
                correlationId,
                List.of()));
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
