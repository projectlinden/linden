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
    private static final UUID THIRD_USER_ID = UUID.fromString("a1b3c5d7-e9f0-4a2b-8c4d-6e8f0a2b4c6d");
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

    @Test
    @DisplayName("a member leaves → only their membership is gone; the owner, the circle and the user row stay")
    void leaveCircle_asMember_removesOnlyMyMembership() {
        // given – CREATOR_ID owns the circle, OTHER_USER_ID is a member
        Circle circle = circleService.createCircle(CREATOR_ID, PAYLOAD);
        appUserRepository.insertIfAbsent(OTHER_USER_ID);
        circleMemberRepository.save(new CircleMember(circle.getId(), OTHER_USER_ID, CircleRole.MEMBER));
        entityManager.flush();
        entityManager.clear();

        // when
        circleService.leaveCircle(circle.getId(), OTHER_USER_ID);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(circleMemberRepository.findById(new CircleMemberId(circle.getId(), OTHER_USER_ID)))
                .as("the leaver's membership").isEmpty();
        CircleMember owner = circleMemberRepository.findById(new CircleMemberId(circle.getId(), CREATOR_ID)).orElseThrow();
        assertThat(owner.getRole()).as("the owner stays owner").isEqualTo(CircleRole.OWNER);
        assertThat(circleRepository.findById(circle.getId())).as("the circle").isPresent();
        assertThat(appUserRepository.findById(OTHER_USER_ID)).as("the leaver's user row").isPresent();
    }

    @Test
    @DisplayName("leave a circle I am not in → NotAMemberException, nothing changes")
    void leaveCircle_asNonMember_throwsNotAMember() {
        // given – the circle exists, but OTHER_USER_ID is not in it
        Circle circle = circleService.createCircle(CREATOR_ID, PAYLOAD);
        entityManager.flush();
        entityManager.clear();

        // when + then
        assertThatExceptionOfType(NotAMemberException.class)
                .isThrownBy(() -> circleService.leaveCircle(circle.getId(), OTHER_USER_ID));
        assertThat(circleMemberRepository.count()).as("memberships – the owner's is untouched").isEqualTo(1);
    }

    @Test
    @DisplayName("the owner leaves while others remain → the longest-standing member becomes owner, the rest stay members")
    void leaveCircle_asOwnerWithOthers_longestStandingMemberBecomesOwner() {
        // given – added in the opposite order to their join times: THIRD_USER_ID is added last, but joined first
        Circle circle = circleService.createCircle(CREATOR_ID, PAYLOAD);
        appUserRepository.insertIfAbsent(OTHER_USER_ID);
        appUserRepository.insertIfAbsent(THIRD_USER_ID);
        circleMemberRepository.save(new CircleMember(circle.getId(), OTHER_USER_ID, CircleRole.MEMBER));
        circleMemberRepository.save(new CircleMember(circle.getId(), THIRD_USER_ID, CircleRole.MEMBER));
        entityManager.flush();
        setJoinedAt(circle.getId(), CREATOR_ID, "2026-10-01T10:00:00Z");
        setJoinedAt(circle.getId(), OTHER_USER_ID, "2026-11-02T10:00:00Z");
        setJoinedAt(circle.getId(), THIRD_USER_ID, "2026-11-01T10:00:00Z");   // the longest-standing after the owner
        entityManager.clear();

        // when
        circleService.leaveCircle(circle.getId(), CREATOR_ID);
        entityManager.flush();
        entityManager.clear();

        // then
        assertThat(circleMemberRepository.findById(new CircleMemberId(circle.getId(), CREATOR_ID)))
                .as("the old owner's membership").isEmpty();
        assertThat(roleOf(circle.getId(), THIRD_USER_ID)).as("the longest-standing member").isEqualTo(CircleRole.OWNER);
        assertThat(roleOf(circle.getId(), OTHER_USER_ID)).as("the newer member").isEqualTo(CircleRole.MEMBER);
    }

    /**
     * now() is the same for every row in one transaction – tests that care about order set the time themselves.
     */
    private void setJoinedAt(UUID circleId, UUID userId, String isoTime) {
        jdbcTemplate.update("UPDATE circle_member SET joined_at = ? WHERE circle_id = ? AND user_id = ?",
                OffsetDateTime.parse(isoTime), circleId, userId);
    }

    private CircleRole roleOf(UUID circleId, UUID userId) {
        return circleMemberRepository.findById(new CircleMemberId(circleId, userId)).orElseThrow().getRole();
    }
}
