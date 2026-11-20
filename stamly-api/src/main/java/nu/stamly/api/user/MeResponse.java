package nu.stamly.api.user;

/**
 * The response of GET /api/v1/me.
 *
 * @param userId the Keycloak {@code sub} claim – the only link between a Keycloak account and Stamly
 */
public record MeResponse(String userId) {
}
