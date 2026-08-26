package io.github.bayonle010.escrow.identity.shared.exception;

import java.util.List;

import io.github.bayonle010.escrow.identity.shared.api.ApiError;
import io.github.bayonle010.escrow.identity.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.identity.shared.api.ErrorCode;
import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

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
        ApiError error = new ApiError(
                ErrorCode.REQUEST_VALIDATION_FAILED,
                "The request contains invalid fields.",
                correlationId(request),
                details);
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(InvalidPasswordException.class)
    ResponseEntity<ApiError> handleInvalidPassword(
            InvalidPasswordException exception,
            HttpServletRequest request) {
        ApiError error = new ApiError(
                ErrorCode.IDENTITY_INVALID_PASSWORD,
                exception.getMessage(),
                correlationId(request),
                List.of(new ApiError.FieldViolation("password", exception.getMessage())));
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(DuplicateEmailException.class)
    ResponseEntity<ApiError> handleDuplicateEmail(
            DuplicateEmailException exception,
            HttpServletRequest request) {
        ApiError error = new ApiError(
                ErrorCode.IDENTITY_EMAIL_ALREADY_REGISTERED,
                exception.getMessage(),
                correlationId(request),
                List.of());
        return ResponseEntity.status(HttpStatus.CONFLICT).body(error);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    ResponseEntity<ApiError> handleUnreadableBody(HttpServletRequest request) {
        ApiError error = new ApiError(
                ErrorCode.REQUEST_BODY_INVALID,
                "The request body is missing or malformed.",
                correlationId(request),
                List.of());
        return ResponseEntity.badRequest().body(error);
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiError> handleUnexpected(Exception exception, HttpServletRequest request) {
        String correlationId = correlationId(request);
        LOGGER.error("Unhandled request failure correlationId={}", correlationId, exception);
        ApiError error = new ApiError(
                ErrorCode.INTERNAL_SERVER_ERROR,
                "The request could not be completed.",
                correlationId,
                List.of());
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
    }

    private String correlationId(HttpServletRequest request) {
        Object value = request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME);
        return value == null ? "unknown" : value.toString();
    }
}
