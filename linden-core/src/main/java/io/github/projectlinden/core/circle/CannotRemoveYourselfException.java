package io.github.projectlinden.core.circle;

import java.util.UUID;

/**
 * Removing yourself is leaving – it has its own rules (a new owner, or the circle is deleted).
 */
public class CannotRemoveYourselfException extends RuntimeException {

    private final UUID circleId;

    public CannotRemoveYourselfException(UUID circleId) {
        super("Use leave instead of removing yourself from circle " + circleId);
        this.circleId = circleId;
    }

    public UUID getCircleId() {
        return circleId;
    }
}
