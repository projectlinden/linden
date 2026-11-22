package io.github.projectlinden.core;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * A throwaway Postgres for repository and service tests in linden-core.
 */
@TestConfiguration(proxyBeanMethods = false)
public class PostgresTestConfiguration {

    /**
     * Keep in step with docker-compose.yml and linden-app's TestcontainersConfiguration.
     */
    static final String POSTGRES_IMAGE = "postgres:17.11";

    @Bean
    @ServiceConnection
    PostgreSQLContainer postgresContainer() {
        return new PostgreSQLContainer(POSTGRES_IMAGE);
    }
}
