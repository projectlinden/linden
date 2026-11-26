package io.github.projectlinden.core.circle;

import java.util.UUID;

/**
 * A member tried something only the circle's owner may do (e.g. remove a member).
 */
public class NotCircleOwnerException extends RuntimeException {

    private final UUID circleId;

    public NotCircleOwnerException(UUID circleId) {
        super("Only the owner of circle " + circleId + " may do this");
        this.circleId = circleId;
    }

    public UUID getCircleId() {
        return circleId;
    }
}
