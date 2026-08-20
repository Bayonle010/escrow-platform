package io.github.bayonle010.escrow.identity.shared;

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
        String correlationId = validCorrelationId(request.getHeader(HEADER_NAME));
        request.setAttribute(ATTRIBUTE_NAME, correlationId);
        response.setHeader(HEADER_NAME, correlationId);
        MDC.put(ATTRIBUTE_NAME, correlationId);
        try {
            filterChain.doFilter(request, response);
        } finally {
            MDC.remove(ATTRIBUTE_NAME);
        }
    }

    private String validCorrelationId(String candidate) {
        if (candidate == null || candidate.isBlank()) {
            return uuidGenerator.generate().toString();
        }

        try {
            return UUID.fromString(candidate).toString();
        } catch (IllegalArgumentException ignored) {
            return uuidGenerator.generate().toString();
        }
    }
}
