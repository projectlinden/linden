package io.github.projectlinden.core.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Stores who is in which circle, and with which role.
 */
public interface CircleMemberRepository extends JpaRepository<CircleMember, CircleMemberId> {

    /**
     * The other members of a circle, longest-standing first (earliest {@code joined_at}; the user id breaks ties, so
     * the order never depends on chance).
     */
    @Query("""
            SELECT m FROM CircleMember m
            WHERE m.id.circleId = :circleId AND m.id.userId <> :excludedUserId
            ORDER BY m.joinedAt, m.id.userId
            """)
    List<CircleMember> findOtherMembersLongestStandingFirst(@Param("circleId") UUID circleId,
                                                            @Param("excludedUserId") UUID excludedUserId);
}
