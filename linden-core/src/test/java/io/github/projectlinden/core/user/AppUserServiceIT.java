package io.github.projectlinden.core.user;

import io.github.projectlinden.core.PostgresTestConfiguration;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.*;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

/**
 * Proves "create on first visit, only once" against a real Postgres – ON CONFLICT cannot be tested with a mock.
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// use the Testcontainers Postgres, never an in-memory database
@Import({PostgresTestConfiguration.class, AppUserService.class})
class AppUserServiceIT {

    private static final int PARALLEL_FIRST_VISITS = 8;

    @Autowired
    private AppUserService appUserService;

    @Autowired
    private AppUserRepository appUserRepository;

    @Test
    @DisplayName("first visit → the user is stored with a database timestamp")
    void getOrCreate_firstVisit_createsUser() {
        // given
        UUID userId = UUID.fromString("5e3f2c9a-1b7d-4c8e-9f60-2a4b6c8d0e1f");

        // when
        AppUser user = appUserService.getOrCreate(userId);

        // then
        assertThat(user.getId()).isEqualTo(userId);
        assertThat(appUserRepository.findById(userId)).as("row in the database").isPresent();
        assertThat(user.getCreatedAt()).as("set by DEFAULT now()").isCloseTo(Instant.now(), within(1, MINUTES));
    }

    @Test
    @DisplayName("second visit → same user, still one row, first timestamp kept")
    void getOrCreate_secondVisit_keepsTheFirstRow() {
        // given
        UUID userId = UUID.fromString("7a1c9e3b-5d2f-4a6b-8c0d-1e3f5a7b9c2d");
        Instant firstVisit = appUserService.getOrCreate(userId).getCreatedAt();

        // when
        AppUser secondVisit = appUserService.getOrCreate(userId);

        // then
        assertThat(appUserRepository.count()).as("rows").isEqualTo(1);
        assertThat(secondVisit.getCreatedAt()).as("first visit kept").isEqualTo(firstVisit);
    }

    @Test
    @DisplayName("parallel first visits → no error and exactly one row")
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
        // each call commits on its own, like real requests
    void getOrCreate_parallelFirstVisits_createExactlyOneRow() throws Exception {
        // given
        UUID userId = UUID.fromString("9c4e1a7d-3b5f-4e2a-8d6c-0f1b3d5e7a9c");
        CountDownLatch startTogether = new CountDownLatch(1);
        Callable<AppUser> firstVisit = () -> {
            startTogether.await();
            return appUserService.getOrCreate(userId);
        };

        try (ExecutorService threads = Executors.newFixedThreadPool(PARALLEL_FIRST_VISITS)) {
            List<Future<AppUser>> visits = new ArrayList<>();
            for (int i = 0; i < PARALLEL_FIRST_VISITS; i++) {
                visits.add(threads.submit(firstVisit));
            }

            // when
            startTogether.countDown();

            // then – get() rethrows any exception from a thread, so one failed visit fails the test
            for (Future<AppUser> visit : visits) {
                assertThat(visit.get().getId()).isEqualTo(userId);
            }
            assertThat(appUserRepository.count()).as("rows").isEqualTo(1);
        } finally {
            appUserRepository.deleteById(userId); // this test committed – clean up for the other tests
        }
    }
}
