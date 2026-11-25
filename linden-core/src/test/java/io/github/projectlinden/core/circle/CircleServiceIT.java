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

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatExceptionOfType;


/**
 * The circle rules against a real Postgres – the foreign keys and the CHECK constraints take part.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import({PostgresTestConfiguration.class, CircleService.class})
class CircleServiceIT {

    private static final UUID CREATOR_ID = UUID.fromString("8b2d4f60-1a3c-4e5f-9b7d-2c4e6f8a0b1d");
    private static final UUID OTHER_USER_ID = UUID.fromString("d4f6a8b0-2c1e-4a3b-8d5f-7e9a1b3c5d70");
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
    private JdbcTemplate jdbcTemplate;

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

    @Test
    @DisplayName("list my circles → only the circles I am a member of (any role), oldest first")
    void findCirclesOf_returnsOnlyMyCircles_oldestFirst() {
        // given – created in the opposite order to the expected result
        Circle mineAndNewer = circleService.createCircle(CREATOR_ID, PAYLOAD);         // I own it
        Circle mineAndOlder = circleService.createCircle(OTHER_USER_ID, PAYLOAD);      // someone else owns it …
        circleMemberRepository.save(new CircleMember(mineAndOlder.getId(), CREATOR_ID, CircleRole.MEMBER)); // … I'm a member
        Circle notMine = circleService.createCircle(OTHER_USER_ID, PAYLOAD);           // I'm not in it
        entityManager.flush();
        setCreatedAt(mineAndNewer.getId(), "2026-11-02T10:00:00Z");
        setCreatedAt(mineAndOlder.getId(), "2026-11-01T10:00:00Z");
        setCreatedAt(notMine.getId(), "2026-10-31T10:00:00Z");   // the oldest of all – first in line if the filter fails
        entityManager.clear();

        // when
        List<Circle> mine = circleService.findCirclesOf(CREATOR_ID);

        // then
        assertThat(mine).extracting(Circle::getId).containsExactly(mineAndOlder.getId(), mineAndNewer.getId());
    }

    /**
     * now() is the same for every row in one transaction – tests that care about order set the time themselves.
     */
    private void setCreatedAt(UUID circleId, String isoTime) {
        jdbcTemplate.update("UPDATE circle SET created_at = ? WHERE id = ?", OffsetDateTime.parse(isoTime), circleId);
    }

    @Test
    @DisplayName("get a circle as a member → the circle")
    void getCircle_asMember_returnsTheCircle() {
        // given
        Circle created = circleService.createCircle(CREATOR_ID, PAYLOAD);
        entityManager.flush();
        entityManager.clear();

        // when
        Circle found = circleService.getCircle(created.getId(), CREATOR_ID);

        // then
        assertThat(found.getId()).isEqualTo(created.getId());
    }

    @Test
    @DisplayName("get a circle I am not in → NotAMemberException")
    void getCircle_asNonMember_throwsNotAMember() {
        // given – the circle exists, but belongs to someone else
        Circle othersCircle = circleService.createCircle(OTHER_USER_ID, PAYLOAD);
        entityManager.flush();
        entityManager.clear();

        // when + then
        assertThatExceptionOfType(NotAMemberException.class)
                .isThrownBy(() -> circleService.getCircle(othersCircle.getId(), CREATOR_ID));
    }

    @Test
    @DisplayName("get a circle that does not exist → the same NotAMemberException, so nobody learns which circles exist")
    void getCircle_thatDoesNotExist_throwsNotAMember() {
        // given
        UUID unknownCircleId = UUID.fromString("0f1e2d3c-4b5a-4968-8776-a5b4c3d2e1f0");

        // when + then
        assertThatExceptionOfType(NotAMemberException.class)
                .isThrownBy(() -> circleService.getCircle(unknownCircleId, CREATOR_ID));
    }
}
