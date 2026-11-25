package io.github.projectlinden.core.circle;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

/**
 * Stores circles. Who is in which circle lives in the membership table.
 */
public interface CircleRepository extends JpaRepository<Circle, UUID> {

    /**
     * The circles a user is a member of (any role), oldest first. The name is encrypted, so the server can only order
     * by time; the id breaks ties, so the order never depends on chance.
     */
    @Query("""
            SELECT c FROM Circle c
            JOIN CircleMember m ON m.id.circleId = c.id
            WHERE m.id.userId = :userId
            ORDER BY c.createdAt, c.id
            """)
    List<Circle> findCirclesOfMember(@Param("userId") UUID userId);
}
