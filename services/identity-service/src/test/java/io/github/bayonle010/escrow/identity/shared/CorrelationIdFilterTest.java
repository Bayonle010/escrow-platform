package io.github.bayonle010.escrow.identity.shared;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

class CorrelationIdFilterTest {

    @Test
    void generatesCorrelationIdInsteadOfUsingAClientProvidedValue() throws Exception {
        CorrelationIdFilter filter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        String clientProvidedId = "019c0000-0000-7000-8000-000000000001";
        request.addHeader(CorrelationIdFilter.HEADER_NAME, clientProvidedId);

        filter.doFilter(request, response, new MockFilterChain());

        String generatedId = response.getHeader(CorrelationIdFilter.HEADER_NAME);
        assertThat(generatedId).isNotEqualTo(clientProvidedId);
        assertThat(UUID.fromString(generatedId).version()).isEqualTo(7);
        assertThat(request.getAttribute(CorrelationIdFilter.ATTRIBUTE_NAME)).isEqualTo(generatedId);
    }
}
