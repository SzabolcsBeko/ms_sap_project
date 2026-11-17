package com.techie.microservices.user.exception;

import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.TreeMap;

/**
 * Maps the service's exceptions to RFC 7807 {@link ProblemDetail} responses with a stable machine
 * {@code code} and, where relevant, a per-field {@code errors} map the SPA can attach to form controls.
 * Internal details are never leaked.
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> errors = new TreeMap<>();
        for (FieldError error : ex.getBindingResult().getFieldErrors()) {
            errors.putIfAbsent(error.getField(), error.getDefaultMessage());
        }
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST,
                "One or more fields are invalid");
        problem.setTitle("Validation failed");
        problem.setProperty("code", "VALIDATION_FAILED");
        problem.setProperty("errors", errors);
        return problem;
    }

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ProblemDetail handleDuplicate(UserAlreadyExistsException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        problem.setTitle("Account already exists");
        problem.setProperty("code", ex.getCode());
        problem.setProperty("errors", Map.of(ex.getField(), ex.getMessage()));
        return problem;
    }

    @ExceptionHandler(WeakPasswordException.class)
    public ProblemDetail handleWeakPassword(WeakPasswordException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        problem.setTitle("Weak password");
        problem.setProperty("code", "WEAK_PASSWORD");
        problem.setProperty("errors", Map.of("password", ex.getMessage()));
        return problem;
    }

    @ExceptionHandler(IdentityProviderUnavailableException.class)
    public ProblemDetail handleIdpUnavailable(IdentityProviderUnavailableException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.SERVICE_UNAVAILABLE, ex.getMessage());
        problem.setTitle("Service unavailable");
        problem.setProperty("code", "IDENTITY_PROVIDER_UNAVAILABLE");
        return problem;
    }

    @ExceptionHandler(ProfileNotFoundException.class)
    public ProblemDetail handleProfileNotFound(ProfileNotFoundException ex) {
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.NOT_FOUND, ex.getMessage());
        problem.setTitle("Profile not found");
        problem.setProperty("code", "PROFILE_NOT_FOUND");
        return problem;
    }

    @ExceptionHandler({RegistrationPersistenceException.class, Exception.class})
    public ProblemDetail handleUnexpected(Exception ex) {
        // Do not leak internals.
        ProblemDetail problem = ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR,
                "An unexpected error occurred, please try again later");
        problem.setTitle("Internal error");
        problem.setProperty("code", "INTERNAL_ERROR");
        return problem;
    }
}
