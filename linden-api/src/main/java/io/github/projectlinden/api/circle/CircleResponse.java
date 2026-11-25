package io.github.projectlinden.api.circle;

import io.github.projectlinden.core.circle.Circle;

import java.util.UUID;

/**
 * A circle as the API returns it: its id and its sealed name (Base64 in JSON). Nothing else – the client needs
 * nothing else (data minimisation).
 */
public record CircleResponse(UUID id, byte[] payload) {

    static CircleResponse from(Circle circle) {
        return new CircleResponse(circle.getId(), circle.getPayload());
    }
}
