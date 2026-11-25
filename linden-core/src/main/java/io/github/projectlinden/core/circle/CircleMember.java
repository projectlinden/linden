package io.github.projectlinden.core.circle;

import jakarta.persistence.*;
import org.jspecify.annotations.Nullable;

import java.time.Instant;
import java.util.UUID;

/**
 * A user's membership in a circle, with their role. Holds plain ids, not entity references: the membership check
 * only needs ids, and plain ids avoid lazy-loading surprises.
 */
@Entity
@Table(name = "circle_member")
public class CircleMember {

    @EmbeddedId
    private CircleMemberId id;

    @Convert(converter = CircleRoleConverter.class)
    @Column(name = "role", nullable = false)
    private CircleRole role;

    @Column(name = "joined_at", nullable = false, insertable = false, updatable = false)
    private @Nullable Instant joinedAt;

    /**
     * For JPA only.
     */
    protected CircleMember() {
    }

    public CircleMember(UUID circleId, UUID userId, CircleRole role) {
        this.id = new CircleMemberId(circleId, userId);
        this.role = role;
    }

    public CircleMemberId getId() {
        return id;
    }

    public CircleRole getRole() {
        return role;
    }

    /**
     * Set by the database on insert – {@code null} until the row has been read back from the database.
     */
    public @Nullable Instant getJoinedAt() {
        return joinedAt;
    }
}
