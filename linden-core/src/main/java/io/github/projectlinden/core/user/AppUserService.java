package io.github.projectlinden.core.user;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

/**
 * Knows users only by their Keycloak id; creates them on their first visit.
 */
@Service
public class AppUserService {

    private final AppUserRepository appUserRepository;

    public AppUserService(AppUserRepository appUserRepository) {
        this.appUserRepository = appUserRepository;
    }

    /**
     * Returns the user, creating them on their first visit. Safe when several first requests arrive at once.
     *
     * @throws IllegalStateException if the row is gone right after the insert (deleted at the same moment)
     */
    @Transactional
    public AppUser getOrCreate(UUID userId) {
        appUserRepository.insertIfAbsent(userId);
        return appUserRepository.findById(userId)
                .orElseThrow(() -> new IllegalStateException("App user vanished right after insert"));
    }
}
