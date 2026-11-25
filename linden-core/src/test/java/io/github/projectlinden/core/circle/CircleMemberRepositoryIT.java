package io.github.projectlinden.core.circle;

import io.github.projectlinden.core.PostgresTestConfiguration;
import io.github.projectlinden.core.user.AppUserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Instant;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(PostgresTestConfiguration.class)
class CircleMemberRepositoryIT {

    private static final UUID CIRCLE_ID = UUID.fromString("3c1e7a52-8f4d-4b6a-9e21-5d7f0a8c4b13");
    private static final UUID OTHER_CIRCLE_ID = UUID.fromString("6a9d2e14-7c3b-4f85-a1d6-0e8b5c7f2a94");
    private static final UUID USER_ID = UUID.fromString("8b2d4f60-1a3c-4e5f-9b7d-2c4e6f8a0b1d");
    private static final UUID OTHER_USER_ID = UUID.fromString("d4f6a8b0-2c1e-4a3b-8d5f-7e9a1b3c5d70");
    private static final byte[] PAYLOAD = {0, 70, 97, 109};

    @Autowired
    private CircleMemberRepository circleMemberRepository;

    @Autowired
    private CircleRepository circleRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    @DisplayName("a saved membership is read back with its role and a database timestamp; the role is stored as lowercase text")
    void save_thenFindById_returnsTheRoleAndADatabaseTimestamp() {
        // given – the circle and the user must exist first (foreign keys)
        appUserRepository.insertIfAbsent(USER_ID);
        circleRepository.save(new Circle(CIRCLE_ID, PAYLOAD));
        CircleMemberId id = new CircleMemberId(CIRCLE_ID, USER_ID);

        // when
        circleMemberRepository.save(new CircleMember(CIRCLE_ID, USER_ID, CircleRole.OWNER));
        entityManager.flush();
        entityManager.clear();
        CircleMember found = circleMemberRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getRole()).isEqualTo(CircleRole.OWNER);
        assertThat(found.getJoinedAt()).isCloseTo(Instant.now(), within(1, MINUTES));

        String storedRole = jdbcTemplate.queryForObject(
                "SELECT role FROM circle_member WHERE circle_id = ? AND user_id = ?",
                String.class, CIRCLE_ID, USER_ID);
        assertThat(storedRole).as("the raw value the database CHECK expects").isEqualTo("owner");
    }

    @Test
    @DisplayName("deleting a circle → its memberships are gone; the users and other circles' memberships stay")
    void deleteCircle_removesItsMemberships_keepsUsersAndOtherCircles() {
        // given – two users in CIRCLE_ID; USER_ID is also in OTHER_CIRCLE_ID
        appUserRepository.insertIfAbsent(USER_ID);
        appUserRepository.insertIfAbsent(OTHER_USER_ID);
        circleRepository.save(new Circle(CIRCLE_ID, PAYLOAD));
        circleRepository.save(new Circle(OTHER_CIRCLE_ID, PAYLOAD));
        circleMemberRepository.save(new CircleMember(CIRCLE_ID, USER_ID, CircleRole.OWNER));
        circleMemberRepository.save(new CircleMember(CIRCLE_ID, OTHER_USER_ID, CircleRole.MEMBER));
        circleMemberRepository.save(new CircleMember(OTHER_CIRCLE_ID, USER_ID, CircleRole.OWNER));
        entityManager.flush();
        entityManager.clear();

        // when
        circleRepository.deleteById(CIRCLE_ID);
        entityManager.flush();   // the DELETE reaches Postgres – its ON DELETE CASCADE runs now

        // then – counted with plain SQL, so JPA's cache can't answer instead of the database
        assertThat(countRows("SELECT count(*) FROM circle_member WHERE circle_id = ?", CIRCLE_ID))
                .as("memberships of the deleted circle").isZero();
        assertThat(countRows("SELECT count(*) FROM circle_member WHERE circle_id = ?", OTHER_CIRCLE_ID))
                .as("memberships of the other circle").isEqualTo(1);
        assertThat(countRows("SELECT count(*) FROM app_user WHERE id IN (?, ?)", USER_ID, OTHER_USER_ID))
                .as("users").isEqualTo(2);
    }

    private int countRows(String sql, Object... parameters) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, parameters);
        return count == null ? 0 : count;
    }
}
