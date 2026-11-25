package io.github.projectlinden.core.circle;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;

import java.io.Serializable;
import java.util.UUID;

/**
 * The primary key of a membership: which circle, which user. A user is in a circle at most once.
 */
@Embeddable
public record CircleMemberId(
        @Column(name = "circle_id") UUID circleId,
        @Column(name = "user_id") UUID userId) implements Serializable {
}
