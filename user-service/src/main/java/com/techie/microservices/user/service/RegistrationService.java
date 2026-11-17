package com.techie.microservices.user.service;

import com.techie.microservices.user.dto.RegistrationRequest;
import com.techie.microservices.user.dto.RegistrationResponse;

/** Registration use case. An interface so the controller depends on the abstraction, not Keycloak. */
public interface RegistrationService {
    RegistrationResponse register(RegistrationRequest request);
}
