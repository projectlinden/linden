package io.github.projectlinden.core.circle;

import java.util.UUID;

/**
 * The person to act on is not a member of the circle.
 */
public class MemberNotFoundException extends RuntimeException {

    private final UUID circleId;

    public MemberNotFoundException(UUID circleId, UUID memberId) {
        super("User " + memberId + " is not a member of circle " + circleId);
        this.circleId = circleId;
    }

    public UUID getCircleId() {
        return circleId;
    }
}
