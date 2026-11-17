package com.techie.microservices.user.service;

import com.techie.microservices.user.dto.RegistrationRequest;
import com.techie.microservices.user.dto.RegistrationResponse;
import com.techie.microservices.user.dto.keycloak.UserRepresentation;
import com.techie.microservices.user.exception.RegistrationPersistenceException;
import com.techie.microservices.user.exception.UserAlreadyExistsException;
import com.techie.microservices.user.model.UserProfile;
import com.techie.microservices.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

/**
 * Orchestrates registration: validate/normalize → create in Keycloak → persist a local profile,
 * with compensation (delete the Keycloak user) if the local write fails, so we never leave a
 * Keycloak account without a matching profile.
 */
@Service
@RequiredArgsConstructor
public class KeycloakRegistrationService implements RegistrationService {

    private static final Logger log = LoggerFactory.getLogger(KeycloakRegistrationService.class);

    private final KeycloakUserService keycloakUserService;
    private final UserProfileRepository userProfileRepository;

    @Override
    public RegistrationResponse register(RegistrationRequest request) {
        String username = request.username().trim().toLowerCase();
        String email = request.email().trim().toLowerCase();
        String firstName = request.firstName().trim();
        String lastName = request.lastName().trim();

        // Fast local pre-check for a friendly 409 without calling Keycloak. Keycloak and the DB
        // unique constraints remain the authoritative guards.
        if (userProfileRepository.existsByUsername(username)) {
            throw UserAlreadyExistsException.username();
        }
        if (userProfileRepository.existsByEmail(email)) {
            throw UserAlreadyExistsException.email();
        }

        UserRepresentation representation =
                UserRepresentation.forRegistration(username, email, firstName, lastName, request.password());

        String keycloakId = keycloakUserService.createUser(representation);

        try {
            UserProfile profile = new UserProfile();
            profile.setKeycloakId(UUID.fromString(keycloakId));
            profile.setUsername(username);
            profile.setEmail(email);
            profile.setFirstName(firstName);
            profile.setLastName(lastName);
            UserProfile saved = userProfileRepository.save(profile);
            log.info("Registered user '{}' (keycloakId={})", username, keycloakId);
            return RegistrationResponse.from(saved);
        } catch (Exception persistenceError) {
            // Undo the Keycloak user so a DB failure can't leave an orphan identity.
            keycloakUserService.deleteUserQuietly(keycloakId);
            throw new RegistrationPersistenceException(
                    "Could not save the user profile; registration was rolled back", persistenceError);
        }
    }
}
