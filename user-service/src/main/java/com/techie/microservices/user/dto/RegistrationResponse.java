package com.techie.microservices.user.dto;

import com.techie.microservices.user.model.UserProfile;

import java.time.Instant;

/** What the SPA gets back on a successful registration (no password, ever). */
public record RegistrationResponse(
        String id,
        String username,
        String email,
        String firstName,
        String lastName,
        Instant createdAt
) {
    public static RegistrationResponse from(UserProfile profile) {
        return new RegistrationResponse(
                profile.getKeycloakId().toString(),
                profile.getUsername(),
                profile.getEmail(),
                profile.getFirstName(),
                profile.getLastName(),
                profile.getCreatedAt());
    }
}
