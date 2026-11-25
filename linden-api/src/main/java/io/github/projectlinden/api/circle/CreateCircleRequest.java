package io.github.projectlinden.api.circle;

import io.github.projectlinden.core.circle.Circle;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import org.jspecify.annotations.Nullable;

/**
 * The body of {@code POST /api/v1/circles}.
 *
 * @param payload the sealed circle name – Base64 in JSON; may be missing in a bad request, so validation checks it
 */
public record CreateCircleRequest(
        @NotNull @Size(min = 1, max = Circle.MAX_PAYLOAD_BYTES) byte @Nullable [] payload) {
}
