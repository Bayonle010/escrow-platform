package io.github.bayonle010.escrow.identity.registration.controller;

import java.net.URI;
import java.util.UUID;

import jakarta.validation.Valid;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestAttribute;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import io.github.bayonle010.escrow.identity.shared.api.ApiResponse;
import io.github.bayonle010.escrow.identity.registration.dto.RegisterUserRequest;
import io.github.bayonle010.escrow.identity.registration.dto.RegisteredUserResponse;
import io.github.bayonle010.escrow.identity.registration.domain.RegisteredUser;
import io.github.bayonle010.escrow.identity.registration.service.RegistrationService;
import io.github.bayonle010.escrow.identity.shared.CorrelationIdFilter;

@RestController
@RequestMapping("/api/v1/auth")
@Tag(name = "Registration", description = "User registration operations")
public class RegistrationController {

    private final RegistrationService registrationService;

    public RegistrationController(RegistrationService registrationService) {
        this.registrationService = registrationService;
    }

    @PostMapping("/register")
    @Operation(
            summary = "Register a user",
            description = "Creates a pending user and its UserRegistered outbox event in one transaction.")
    public ResponseEntity<ApiResponse<RegisteredUserResponse>> register(
            @Valid @RequestBody RegisterUserRequest request,
            @Parameter(hidden = true)
            @RequestAttribute(CorrelationIdFilter.ATTRIBUTE_NAME) UUID correlationId) {
        RegisteredUser registeredUser = registrationService.register(
                request.email(),
                request.password(),
                correlationId);

        RegisteredUserResponse response = RegisteredUserResponse.from(registeredUser);

        return ResponseEntity
                .created(URI.create("/api/v1/users/" + registeredUser.userId()))
                .body(new ApiResponse<>(response, correlationId.toString()));
    }
}
