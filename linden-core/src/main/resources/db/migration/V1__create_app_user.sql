-- One row per person who has used Linden.
-- The id is Keycloak's "sub" claim (ADR-0002): the only link between an account and the app.
-- Nothing else is copied from Keycloak – no email, no name, no password.
CREATE TABLE app_user
(
    id         uuid PRIMARY KEY,
    created_at timestamptz NOT NULL DEFAULT now()
);
