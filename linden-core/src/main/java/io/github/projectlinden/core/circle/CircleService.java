package io.github.projectlinden.core.circle;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * The rules for circles: creating them and, later, who may see and change them.
 */
@Service
public class CircleService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;

    public CircleService(CircleRepository circleRepository, CircleMemberRepository circleMemberRepository) {
        this.circleRepository = circleRepository;
        this.circleMemberRepository = circleMemberRepository;
    }

    /**
     * Creates a circle with a sealed name; its creator becomes its owner and only member. Both rows are written in one
     * transaction, so a circle never exists without its owner.
     *
     * @param creatorId the creator's user id (the Keycloak {@code sub})
     * @param payload   the sealed circle name, 1 to {@link Circle#MAX_PAYLOAD_BYTES} bytes
     * @return the new circle
     * @throws IllegalArgumentException if the payload is empty or too large – nothing is written
     */
    @Transactional
    public Circle createCircle(UUID creatorId, byte[] payload) {
        Circle circle = circleRepository.save(new Circle(UUID.randomUUID(), payload));
        circleMemberRepository.save(new CircleMember(circle.getId(), creatorId, CircleRole.OWNER));
        return circle;
    }
}
