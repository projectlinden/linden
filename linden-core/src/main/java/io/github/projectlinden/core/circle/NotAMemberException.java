package io.github.projectlinden.core.circle;

import java.util.UUID;

/**
 * The user is not a member of the circle – or the circle does not exist. Both cases are deliberately the same, so
 * nobody can find out which circles exist by trying ids.
 */
public class NotAMemberException extends RuntimeException {

    private final UUID circleId;

    public NotAMemberException(UUID circleId) {
        super("Not a member of circle " + circleId + " (or it does not exist)");
        this.circleId = circleId;
    }

    public UUID getCircleId() {
        return circleId;
    }
}
