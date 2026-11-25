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

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The circle rules against a real Postgres – the foreign keys and the CHECK constraints take part.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfiguration.class, CircleService.class})
class CircleServiceIT {

    private static final UUID CREATOR_ID = UUID.fromString("8b2d4f60-1a3c-4e5f-9b7d-2c4e6f8a0b1d");
    private static final byte[] PAYLOAD = {0, 70, 97, 109};

    @Autowired
    private CircleService circleService;

    @Autowired
    private CircleRepository circleRepository;

    @Autowired
    private CircleMemberRepository circleMemberRepository;

    @Autowired
    private AppUserRepository appUserRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("create a circle → the circle is stored, and its creator is its only member, as owner")
    void createCircle_storesTheCircleWithTheCreatorAsOwner() {
        // given
        appUserRepository.insertIfAbsent(CREATOR_ID);

        // when
        Circle created = circleService.createCircle(CREATOR_ID, PAYLOAD);
        entityManager.flush();
        entityManager.clear();

        // then
        UUID circleId = created.getId();
        assertThat(circleRepository.findById(circleId)).as("the circle in the database").isPresent();

        CircleMember owner = circleMemberRepository.findById(new CircleMemberId(circleId, CREATOR_ID)).orElseThrow();
        assertThat(owner.getRole()).isEqualTo(CircleRole.OWNER);
        assertThat(circleMemberRepository.count()).as("memberships – only the creator's").isEqualTo(1);
    }
    
    @Test
    @DisplayName("a creator without an app_user row yet (never called /api/v1/me) → the circle is created, and the user row too")
    void createCircle_forACreatorWithoutAUserRow_createsTheUserRowToo() {
        // given – deliberately no insertIfAbsent: this user has never been seen by the app

        // when
        Circle created = circleService.createCircle(CREATOR_ID, PAYLOAD);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(appUserRepository.findById(CREATOR_ID)).as("the creator's user row").isPresent();
        assertThat(circleMemberRepository.findById(new CircleMemberId(created.getId(), CREATOR_ID)))
                .as("the owner membership").isPresent();
    }

}
