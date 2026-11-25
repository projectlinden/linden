package io.github.projectlinden.api.user;

import io.github.projectlinden.api.security.AuthenticatedUser;
import io.github.projectlinden.core.user.AppUser;
import io.github.projectlinden.core.user.AppUserService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tells the logged-in user who they are – and creates them on their first visit.
 * There is no user id in the URL: the identity comes only from the signed JWT.
 */
@RestController
@RequestMapping("/api/v1/me")
class MeController {

    private final AppUserService appUserService;

    MeController(AppUserService appUserService) {
        this.appUserService = appUserService;
    }

    @GetMapping
    MeResponse getCurrentUser(@AuthenticationPrincipal Jwt token) {
        // The security door (UuidSubjectAuthenticationConverter) only lets tokens with a lowercase UUID sub through
        AppUser user = appUserService.getOrCreate(AuthenticatedUser.idOf(token));
        return new MeResponse(user.getId());
    }
}
