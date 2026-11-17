package com.techie.microservices.user.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/** The subset of Keycloak's UserRepresentation we send when creating a user. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record UserRepresentation(
        String username,
        String email,
        String firstName,
        String lastName,
        Boolean enabled,
        Boolean emailVerified,
        List<CredentialRepresentation> credentials
) {
    public static UserRepresentation forRegistration(String username, String email,
                                                     String firstName, String lastName, String password) {
        return new UserRepresentation(username, email, firstName, lastName,
                true, false, List.of(CredentialRepresentation.password(password)));
    }
}
