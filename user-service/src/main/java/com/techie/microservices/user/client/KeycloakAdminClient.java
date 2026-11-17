package com.techie.microservices.user.client;

import com.techie.microservices.user.dto.keycloak.UserRepresentation;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.service.annotation.DeleteExchange;
import org.springframework.web.service.annotation.HttpExchange;
import org.springframework.web.service.annotation.PostExchange;

/**
 * The slice of the Keycloak Admin REST API that user-service needs: create a user and (for
 * compensation) delete one. Intentionally narrow — see §5.2 of the implementation plan for why we
 * don't pull in keycloak-admin-client.
 */
@HttpExchange
public interface KeycloakAdminClient {

    /** Creates a user. Keycloak answers 201 with a {@code Location} header pointing at the new user. */
    @PostExchange("/admin/realms/{realm}/users")
    ResponseEntity<Void> createUser(@PathVariable String realm, @RequestBody UserRepresentation user);

    /** Deletes a user by Keycloak id. Used to undo a created user when the local DB write fails. */
    @DeleteExchange("/admin/realms/{realm}/users/{id}")
    void deleteUser(@PathVariable String realm, @PathVariable String id);
}
