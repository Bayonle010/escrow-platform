package io.github.bayonle010.escrow.identity.registration.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record RegisterUserRequest(
        @NotBlank(message = "Email is required.")
        @Email(message = "Email must be valid.")
        @Size(max = 320, message = "Email must be at most 320 characters.")
        @Schema(description = "User email address", example = "alice@example.com")
        String email,

        @NotBlank(message = "Password is required.")
        @Size(min = 12, max = 72, message = "Password must be between 12 and 72 characters.")
        @Pattern(
                regexp = "^(?=.*[0-9])(?=.*[a-z])(?=.*[A-Z])(?=.*[!@#$%^&*()\\-_=+{}\\[\\]|;:'\",.<>?/`~])(?=\\S+$).+$",
                message = "Password must contain at least one uppercase letter, one lowercase letter, one number, and one special character, with no whitespace.")
        @Schema(
                description = "Password with uppercase, lowercase, number, and special characters",
                example = "A-secure-password1!",
                format = "password")
        String password) {
}
