package com.techie.microservices.user.exception;

import lombok.Getter;

/** Username or email already taken. Carries the machine code and the offending field for the SPA. */
@Getter
public class UserAlreadyExistsException extends RuntimeException {

    private final String code;   // USERNAME_TAKEN | EMAIL_TAKEN
    private final String field;  // username | email

    public UserAlreadyExistsException(String code, String field, String message) {
        super(message);
        this.code = code;
        this.field = field;
    }

    public static UserAlreadyExistsException username() {
        return new UserAlreadyExistsException("USERNAME_TAKEN", "username", "This username is already taken");
    }

    public static UserAlreadyExistsException email() {
        return new UserAlreadyExistsException("EMAIL_TAKEN", "email", "An account with this email already exists");
    }
}
