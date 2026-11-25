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
    private static final UUID USER_ID = UUID.fromString("8b2d4f60-1a3c-4e5f-9b7d-2c4e6f8a0b1d");

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
        circleRepository.save(new Circle(CIRCLE_ID, new byte[]{0, 70, 97, 109}));
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
}
