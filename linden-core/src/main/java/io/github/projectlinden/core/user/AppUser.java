package io.github.projectlinden.core.user;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * A person who has used Linden. The id is Keycloak's {@code sub}; nothing else is copied from Keycloak.
 * Rows are only created by {@link AppUserRepository#insertIfAbsent(UUID)}, so the database sets {@code createdAt}.
 */
@Entity
@Table(name = "app_user")
public class AppUser {

    @Id
    private UUID id;

    // Set by the database (DEFAULT now()), never by Java.
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private Instant createdAt;

    /**
     * For JPA only.
     */
    protected AppUser() {
    }

    public UUID getId() {
        return id;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
