package io.github.projectlinden;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A throwaway Postgres for every test that starts the whole app (@SpringBootTest).
 * Same version as docker-compose.yml, so migrations are tested against what we run.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

    /**
     * Keep in step with the postgres image in docker-compose.yml.
     */
    static final String POSTGRES_IMAGE = "postgres:17.X";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
