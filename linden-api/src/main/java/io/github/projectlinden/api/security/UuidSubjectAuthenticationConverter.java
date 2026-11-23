package io.github.projectlinden.api.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;

import java.util.regex.Pattern;

/**
 * The last check at the door: a token is only accepted if its {@code sub} is a canonical, lowercase UUID – the form
 * Keycloak issues, and the type of {@code app_user.id} (ADR-0002). Anything else is rejected with
 * {@code 401 invalid_token} before any controller runs, so every endpoint can trust the subject.
 *
 * <p>Why strict: {@code UUID.fromString} also accepts {@code 1-2-3-4-5} and uppercase, so two different sub strings
 * could otherwise become the same user.
 */
final class UuidSubjectAuthenticationConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    private static final Logger LOGGER = LoggerFactory.getLogger(UuidSubjectAuthenticationConverter.class);

    private static final Pattern CANONICAL_UUID =
            Pattern.compile("[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}");

    private final JwtAuthenticationConverter springDefault = new JwtAuthenticationConverter();

    @Override
    public AbstractAuthenticationToken convert(Jwt token) {
        String subject = token.getSubject();
        if (subject == null) {
            // Only our own config can cause this (signatures are checked first) – e.g. the realm lost its "basic" scope
            LOGGER.warn("Token rejected: it has no sub claim. Check the realm's 'basic' client scope.");
            throw new InvalidBearerTokenException("The token has no subject");
        }
        if (!CANONICAL_UUID.matcher(subject).matches()) {
            // Never log the value: a federated sub (f:<provider>:<username>) contains a username
            LOGGER.warn("Token rejected: its sub claim is not a lowercase UUID.");
            throw new InvalidBearerTokenException("The token's subject is not a valid user id");
        }
        return springDefault.convert(token);
    }
}
