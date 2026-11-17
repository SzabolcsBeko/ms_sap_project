package com.techie.microservices.user.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * Registration input. Validation mirrors the Keycloak password policy and the Angular form (§4.1).
 * {@code toString()} is overridden so the password never reaches a log.
 */
public record RegistrationRequest(

        @NotBlank(message = "Username is required")
        @Size(min = 3, max = 50, message = "Username must be 3-50 characters")
        @Pattern(regexp = "^[a-zA-Z0-9._-]+$", message = "Username may contain letters, digits, dot, underscore and hyphen")
        String username,

        @NotBlank(message = "Email is required")
        @Email(message = "Email is not valid")
        @Size(max = 254, message = "Email must be at most 254 characters")
        String email,

        @NotBlank(message = "First name is required")
        @Size(min = 1, max = 100, message = "First name must be 1-100 characters")
        String firstName,

        @NotBlank(message = "Last name is required")
        @Size(min = 1, max = 100, message = "Last name must be 1-100 characters")
        String lastName,

        @NotBlank(message = "Password is required")
        @Size(min = 8, max = 128, message = "Password must be 8-128 characters")
        @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d).+$",
                message = "Password must contain at least one lower-case letter, one upper-case letter and one digit")
        String password

) {
    @Override
    public String toString() {
        return "RegistrationRequest[username=%s, email=%s, firstName=%s, lastName=%s, password=***]"
                .formatted(username, email, firstName, lastName);
    }
}
