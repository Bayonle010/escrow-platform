package io.github.bayonle010.escrow.identity.registration.api;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.util.UUID;

import io.github.bayonle010.escrow.identity.registration.controller.RegistrationController;
import io.github.bayonle010.escrow.identity.shared.exception.ApiExceptionHandler;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import io.github.bayonle010.escrow.identity.registration.service.RegistrationService;
import io.github.bayonle010.escrow.identity.registration.domain.RegisteredUser;
import io.github.bayonle010.escrow.identity.registration.domain.UserStatus;
import io.github.bayonle010.escrow.identity.shared.CorrelationIdFilter;
import io.github.bayonle010.escrow.identity.shared.UuidV7Generator;

class RegistrationControllerTest {

    private final RegistrationService registrationService = mock(RegistrationService.class);
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        var controller = new RegistrationController(registrationService);
        var correlationFilter = new CorrelationIdFilter(new UuidV7Generator(Clock.systemUTC()));
        mockMvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .addFilters(correlationFilter)
                .build();
    }

    @Test
    void returnsCreatedResourceInTheStandardEnvelope() throws Exception {
        UUID userId = UUID.fromString("019c0000-0000-7000-8000-000000000001");
        when(registrationService.register(anyString(), anyString())).thenReturn(new RegisteredUser(
                userId,
                "alice@example.com",
                UserStatus.PENDING_VERIFICATION,
                Instant.parse("2026-08-20T12:00:00Z")));

        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"A-secure-password1!"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(header().string("Location", "/api/v1/users/" + userId))
                .andExpect(header().exists("X-Correlation-Id"))
                .andExpect(jsonPath("$.data.id").value(userId.toString()))
                .andExpect(jsonPath("$.data.email").value("alice@example.com"))
                .andExpect(jsonPath("$.data.status").value("PENDING_VERIFICATION"))
                .andExpect(jsonPath("$.correlationId").isNotEmpty());
    }

    @Test
    void rejectsInvalidInputBeforeCallingTheApplicationService() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"not-an-email","password":"short"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details").isNotEmpty());

        verifyNoInteractions(registrationService);
    }

    @Test
    void rejectsPasswordWithoutRequiredCharacterGroups() throws Exception {
        mockMvc.perform(post("/api/v1/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"alice@example.com","password":"onlylowercase"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("REQUEST_VALIDATION_FAILED"))
                .andExpect(jsonPath("$.details[0].field").value("password"));

        verifyNoInteractions(registrationService);
    }
}
