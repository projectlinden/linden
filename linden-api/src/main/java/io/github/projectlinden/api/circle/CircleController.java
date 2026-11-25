package io.github.projectlinden.api.circle;

import io.github.projectlinden.api.security.AuthenticatedUser;
import io.github.projectlinden.core.circle.Circle;
import io.github.projectlinden.core.circle.CircleService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.support.ServletUriComponentsBuilder;

import java.net.URI;
import java.util.Objects;
import java.util.UUID;

/**
 * Circles of the logged-in user. Every rule lives in {@link CircleService}; this class only speaks HTTP.
 */
@RestController
@RequestMapping("/api/v1/circles")
class CircleController {

    private final CircleService circleService;

    CircleController(CircleService circleService) {
        this.circleService = circleService;
    }

    @PostMapping
    ResponseEntity<CircleResponse> createCircle(@AuthenticationPrincipal Jwt token,
                                                @Valid @RequestBody CreateCircleRequest request) {
        // @Valid has checked the payload: present, 1 to MAX_PAYLOAD_BYTES bytes
        byte[] payload = Objects.requireNonNull(request.payload());
        Circle circle = circleService.createCircle(AuthenticatedUser.idOf(token), payload);
        URI location = ServletUriComponentsBuilder.fromCurrentRequest()
                .path("/{id}").buildAndExpand(circle.getId()).toUri();
        return ResponseEntity.created(location).body(CircleResponse.from(circle));
    }

    /**
     * My circles, oldest first – the order comes from {@link CircleService} and is kept as it is.
     */
    @GetMapping
    CircleListResponse listCircles(@AuthenticationPrincipal Jwt token) {
        return CircleListResponse.from(circleService.findCirclesOf(AuthenticatedUser.idOf(token)));
    }

    /**
     * One circle, for one of its members. Not a member – or no such circle – becomes 404 (ApiExceptionHandler).
     */
    @GetMapping("/{circleId}")
    CircleResponse getCircle(@AuthenticationPrincipal Jwt token, @PathVariable("circleId") UUID circleId) {
        return CircleResponse.from(circleService.getCircle(circleId, AuthenticatedUser.idOf(token)));
    }
}
