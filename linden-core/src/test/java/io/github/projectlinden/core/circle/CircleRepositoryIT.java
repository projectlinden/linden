package io.github.projectlinden.core.circle;

import io.github.projectlinden.core.PostgresTestConfiguration;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;

import java.time.Instant;
import java.util.UUID;

import static java.time.temporal.ChronoUnit.MINUTES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;


@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
// use the Testcontainers Postgres, never an in-memory database
@Import({PostgresTestConfiguration.class})
class CircleRepositoryIT {

    @Autowired
    private CircleRepository circleRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("a saved kreds is read back from Postgres with the same id, payload bytes and a database timestamp")
    void save_thenFindById_returnsTheSamePayloadAndADatabaseTimestamp() {
        // given
        UUID id = UUID.fromString("ef71a788-3695-418d-8466-08ca3bedf3e3");
        byte[] payload = new byte[]{0, 70, 97, 100};
        Circle circle = new Circle(id, payload);

        // when
        circleRepository.save(circle);
        entityManager.flush();   // write the INSERT to Postgres now
        entityManager.clear();   // forget every cached entity
        Circle found = circleRepository.findById(id).orElseThrow();

        // then
        assertThat(found.getPayload()).containsExactly(payload);
        assertThat(found.getId()).isEqualTo(id);
        assertThat(found.getCreatedAt()).isCloseTo(Instant.now(), within(1, MINUTES));

    }
}
