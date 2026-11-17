package com.techie.microservices.user.exception;

/** No local profile for the authenticated user (e.g. the seeded testuser). Surfaces as 404. */
public class ProfileNotFoundException extends RuntimeException {
    public ProfileNotFoundException(String message) {
        super(message);
    }
}
