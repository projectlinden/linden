package io.github.projectlinden.core.circle;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

/**
 * Stores kredse. Who is in which kreds lives in the membership table.
 */
public interface CircleRepository extends JpaRepository<Circle, UUID> {
}
