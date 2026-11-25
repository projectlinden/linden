package io.github.projectlinden.api.security;

import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Objects;
import java.util.UUID;

/**
 * The logged-in user's id, read from the token – the only way controllers learn who is calling.
 */
public final class AuthenticatedUser {

    private AuthenticatedUser() {
    }

    /**
     * The user id (the Keycloak {@code sub}). Safe to parse: the security door ({@code UuidSubjectAuthenticationConverter})
     * only lets tokens with a lowercase UUID {@code sub} through.
     */
    public static UUID idOf(Jwt token) {
        String subject = Objects.requireNonNull(token.getSubject(), "No sub – the security door should have rejected this token");
        return UUID.fromString(subject);
    }
}
