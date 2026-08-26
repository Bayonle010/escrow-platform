package io.github.bayonle010.escrow.payment.shared;

import java.io.IOException;
import java.util.UUID;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public final class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER_NAME = "X-Correlation-Id";
    public static final String ATTRIBUTE_NAME = "correlationId";

    private final UuidV7Generator uuidGenerator;

    public CorrelationIdFilter(UuidV7Generator uuidGenerator) {
        this.uuidGenerator = uuidGenerator;
    }

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {
        UUID correlationId = parseOrGenerate(request.getHeader(HEADER_NAME));
        String value = correlationId.toString();
        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        response.setHeader(HEADER_NAME, value);
        MDC.put(ATTRIBUTE_NAME, value);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ATTRIBUTE_NAME);
        }
    }

    private UUID parseOrGenerate(String value) {
        if (value != null) {
            try {
                return UUID.fromString(value);
            } catch (IllegalArgumentException ignored) {
                // Invalid external correlation values are replaced at the service boundary.
            }
        }
        return uuidGenerator.generate();
    }
}
