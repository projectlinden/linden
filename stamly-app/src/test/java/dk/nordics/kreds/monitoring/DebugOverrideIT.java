package dk.nordics.kreds.monitoring;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves that DEBUG can be switched on deliberately for one package. On a server this is the environment variable
 * {@code LOGGING_LEVEL_DK_NORDICS_KREDS=DEBUG} – Spring Boot maps it to the property used here.
 */
@SpringBootTest(properties = "logging.level.dk.nordics.kreds=DEBUG")
class DebugOverrideIT {

    @Test
    @DisplayName("logging.level.dk.nordics.kreds=DEBUG → kreds code logs DEBUG, libraries stay at INFO")
    void debugOverride_appliesToKredsOnly() {
        assertThat(LoggerFactory.getLogger("dk.nordics.kreds").isDebugEnabled()).as("kreds DEBUG").isTrue();
        assertThat(LoggerFactory.getLogger("org.springframework.security").isDebugEnabled())
                .as("spring security DEBUG").isFalse();
    }
}
