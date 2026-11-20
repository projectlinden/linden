package nu.stamly.api;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Spring Boot configuration for the slice tests of stamly-api.
 * The real application class lives in stamly-app; this one exists only so @WebMvcTest has a starting point.
 */
@SpringBootApplication
class ApiTestApplication {
}