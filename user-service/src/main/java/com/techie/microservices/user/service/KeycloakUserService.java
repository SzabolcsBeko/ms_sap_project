package com.techie.microservices.user.service;

import com.techie.microservices.user.client.KeycloakAdminClient;
import com.techie.microservices.user.dto.keycloak.UserRepresentation;
import com.techie.microservices.user.exception.IdentityProviderUnavailableException;
import com.techie.microservices.user.exception.UserAlreadyExistsException;
import com.techie.microservices.user.exception.WeakPasswordException;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;

import java.net.URI;

/**
 * Infrastructure adapter around the Keycloak Admin API. Translates Keycloak's HTTP errors into the
 * service's domain exceptions and guards the call with a circuit breaker:
 * <ul>
 *   <li>409 → {@link UserAlreadyExistsException} (username/email) — a <em>business</em> outcome,
 *       configured as an ignored exception so it never trips the breaker.</li>
 *   <li>400 → {@link WeakPasswordException} — also ignored by the breaker.</li>
 *   <li>connectivity failure / open circuit → fallback → {@link IdentityProviderUnavailableException} (503).</li>
 * </ul>
 */
@Service
public class KeycloakUserService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakUserService.class);

    private final KeycloakAdminClient keycloakAdminClient;
    private final String realm;

    public KeycloakUserService(KeycloakAdminClient keycloakAdminClient,
                               @Value("${keycloak.realm}") String realm) {
        this.keycloakAdminClient = keycloakAdminClient;
        this.realm = realm;
    }

    /** Creates the user in Keycloak and returns its id (the last segment of the Location header). */
    @CircuitBreaker(name = "keycloak", fallbackMethod = "createUserFallback")
    public String createUser(UserRepresentation user) {
        try {
            ResponseEntity<Void> response = keycloakAdminClient.createUser(realm, user);
            URI location = response.getHeaders().getLocation();
            if (location == null) {
                throw new IllegalStateException("Keycloak did not return a Location header for the created user");
            }
            String path = location.getPath();
            return path.substring(path.lastIndexOf('/') + 1);
        } catch (HttpClientErrorException.Conflict conflict) {
            throw mapConflict(conflict);
        } catch (HttpClientErrorException.BadRequest badRequest) {
            throw new WeakPasswordException("Password does not meet the required policy");
        }
    }

    @SuppressWarnings("unused") // invoked by Resilience4j for non-ignored (connectivity) failures
    private String createUserFallback(UserRepresentation user, Throwable throwable) {
        // Ignored exceptions (duplicate / weak password) are rethrown by Resilience4j and never land here,
        // but keep the guard so an unexpected wrapping can't mask a business error as a 503.
        if (throwable instanceof UserAlreadyExistsException e) {
            throw e;
        }
        if (throwable instanceof WeakPasswordException e) {
            throw e;
        }
        log.error("Keycloak create-user call failed: {}", throwable.getMessage());
        throw new IdentityProviderUnavailableException(
                "The identity provider is unavailable, please try again later", throwable);
    }

    /** Best-effort compensation: delete a just-created Keycloak user. Never throws. */
    public void deleteUserQuietly(String keycloakId) {
        try {
            keycloakAdminClient.deleteUser(realm, keycloakId);
        } catch (Exception ex) {
            // Leave an orphan rather than fail the whole request a second time. Logged so it can be
            // reconciled by hand. Never log credentials.
            log.error("Compensation failed: could not delete orphaned Keycloak user {}", keycloakId, ex);
        }
    }

    private UserAlreadyExistsException mapConflict(HttpClientErrorException conflict) {
        String body = conflict.getResponseBodyAsString().toLowerCase();
        if (body.contains("email")) {
            return UserAlreadyExistsException.email();
        }
        return UserAlreadyExistsException.username();
    }
}
