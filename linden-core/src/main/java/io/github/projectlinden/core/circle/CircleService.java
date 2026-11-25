package io.github.projectlinden.core.circle;

import io.github.projectlinden.core.user.AppUserRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * The rules for circles: creating them and, later, who may see and change them.
 */
@Service
public class CircleService {

    private final CircleRepository circleRepository;
    private final CircleMemberRepository circleMemberRepository;
    private final AppUserRepository appUserRepository;

    public CircleService(CircleRepository circleRepository, CircleMemberRepository circleMemberRepository,
                         AppUserRepository appUserRepository) {
        this.circleRepository = circleRepository;
        this.circleMemberRepository = circleMemberRepository;
        this.appUserRepository = appUserRepository;
    }

    /**
     * Creates a circle with a sealed name; its creator becomes its owner and only member. Both rows are written in one
     * transaction, so a circle never exists without its owner. The creator's user row is created if this is their
     * first request.
     *
     * @param creatorId the creator's user id (the Keycloak {@code sub})
     * @param payload   the sealed circle name, 1 to {@link Circle#MAX_PAYLOAD_BYTES} bytes
     * @return the new circle
     * @throws IllegalArgumentException if the payload is empty or too large – nothing is written
     */
    @Transactional
    public Circle createCircle(UUID creatorId, byte[] payload) {
        Circle circle = new Circle(UUID.randomUUID(), payload);   // validates the payload before anything is written
        appUserRepository.insertIfAbsent(creatorId);              // this may be the creator's very first request
        Circle saved = circleRepository.save(circle);
        circleMemberRepository.save(new CircleMember(saved.getId(), creatorId, CircleRole.OWNER));
        return saved;
    }

    /**
     * The circles a user is a member of (any role), oldest first. A user the app has never seen simply has none –
     * this only reads, it never creates a user row.
     */
    @Transactional(readOnly = true)
    public List<Circle> findCirclesOf(UUID userId) {
        return List.copyOf(circleRepository.findCirclesOfMember(userId));
    }

    /**
     * A circle, for one of its members.
     *
     * @throws NotAMemberException if the user is not a member – or the circle does not exist (deliberately the same)
     */
    @Transactional(readOnly = true)
    public Circle getCircle(UUID circleId, UUID userId) {
        getMembership(circleId, userId);
        return circleRepository.findById(circleId)
                // a membership guarantees the circle exists (foreign key) – unless it was deleted a moment ago,
                // and then "not a member" is exactly the right answer
                .orElseThrow(() -> new NotAMemberException(circleId));
    }

    /**
     * The user's membership in a circle – the check every circle operation starts with. The role inside it tells
     * whether the user may change the circle.
     *
     * @throws NotAMemberException if the user is not a member – or the circle does not exist (deliberately the same)
     */
    @Transactional(readOnly = true)
    public CircleMember getMembership(UUID circleId, UUID userId) {
        return circleMemberRepository.findById(new CircleMemberId(circleId, userId))
                .orElseThrow(() -> new NotAMemberException(circleId));
    }

    /**
     * The user leaves the circle – any member, at any time (VISION.md § 5). When the owner leaves while others remain,
     * the longest-standing member becomes owner, so a circle never ends up without one.
     *
     * @throws NotAMemberException if the user is not a member – or the circle does not exist (deliberately the same)
     */
    @Transactional
    public void leaveCircle(UUID circleId, UUID userId) {
        CircleMember membership = getMembership(circleId, userId);
        if (membership.getRole() == CircleRole.OWNER) {
            circleMemberRepository.findOtherMembersLongestStandingFirst(circleId, userId).stream()
                    .findFirst()
                    .ifPresent(CircleMember::becomeOwner);
        }
        circleMemberRepository.delete(membership);
    }
}
