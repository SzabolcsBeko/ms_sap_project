package com.techie.microservices.user.controller;

import com.techie.microservices.user.dto.RegistrationRequest;
import com.techie.microservices.user.dto.RegistrationResponse;
import com.techie.microservices.user.exception.ProfileNotFoundException;
import com.techie.microservices.user.repository.UserProfileRepository;
import com.techie.microservices.user.service.RegistrationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/user")
@RequiredArgsConstructor
public class UserController {

    private final RegistrationService registrationService;
    private final UserProfileRepository userProfileRepository;

    /** Public: create an account. */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public RegistrationResponse register(@Valid @RequestBody RegistrationRequest request) {
        return registrationService.register(request);
    }

    /** Authenticated: the caller's own profile, keyed by the JWT subject. */
    @GetMapping("/me")
    public RegistrationResponse me(@AuthenticationPrincipal Jwt jwt) {
        UUID keycloakId = UUID.fromString(jwt.getSubject());
        return userProfileRepository.findByKeycloakId(keycloakId)
                .map(RegistrationResponse::from)
                .orElseThrow(() -> new ProfileNotFoundException("No profile exists for the current user"));
    }
}
