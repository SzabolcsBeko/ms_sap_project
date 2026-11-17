package com.techie.microservices.user.exception;

/**
 * The user was created in Keycloak but the local profile could not be saved. By the time this is
 * thrown the Keycloak user has already been deleted again (compensation). Surfaces as 500.
 */
public class RegistrationPersistenceException extends RuntimeException {
    public RegistrationPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}
