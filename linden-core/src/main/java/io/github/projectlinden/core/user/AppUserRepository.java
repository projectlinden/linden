package io.github.projectlinden.core.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface AppUserRepository extends JpaRepository<AppUser, UUID> {

    /**
     * Creates the user if they do not exist yet – atomically, so parallel first requests never collide.
     *
     * @return 1 if a row was created, 0 if the user already existed
     */
    @Modifying
    @Query(value = "INSERT INTO app_user (id) VALUES (:id) ON CONFLICT (id) DO NOTHING", nativeQuery = true)
    int insertIfAbsent(@Param("id") UUID id);
}
