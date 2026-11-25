package io.github.projectlinden.api.circle;

import io.github.projectlinden.core.circle.Circle;

import java.util.List;

/**
 * The circles of the logged-in user, oldest first. An object around the list, so fields like paging can be added
 * later without breaking clients.
 */
public record CircleListResponse(List<CircleResponse> circles) {

    static CircleListResponse from(List<Circle> circles) {
        return new CircleListResponse(circles.stream().map(CircleResponse::from).toList());
    }
}
