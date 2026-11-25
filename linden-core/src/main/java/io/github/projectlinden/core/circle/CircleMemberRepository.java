package io.github.projectlinden.core.circle;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Stores who is in which circle, and with which role.
 */
public interface CircleMemberRepository extends JpaRepository<CircleMember, CircleMemberId> {
}
