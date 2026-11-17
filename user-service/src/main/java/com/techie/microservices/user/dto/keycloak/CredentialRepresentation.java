package com.techie.microservices.user.dto.keycloak;

import com.fasterxml.jackson.annotation.JsonInclude;

/** A Keycloak credential. We only ever send a non-temporary password. */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record CredentialRepresentation(String type, String value, Boolean temporary) {

    public static CredentialRepresentation password(String value) {
        return new CredentialRepresentation("password", value, false);
    }
}
