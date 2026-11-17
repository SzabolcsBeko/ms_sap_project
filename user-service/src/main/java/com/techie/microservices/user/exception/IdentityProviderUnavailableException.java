package com.techie.microservices.user.exception;

/** Keycloak was unreachable, timed out, or the circuit is open. Surfaces as 503. */
public class IdentityProviderUnavailableException extends RuntimeException {
    public IdentityProviderUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
