package com.techie.microservices.user.exception;

/** Keycloak rejected the password against the realm password policy. */
public class WeakPasswordException extends RuntimeException {
    public WeakPasswordException(String message) {
        super(message);
    }
}
