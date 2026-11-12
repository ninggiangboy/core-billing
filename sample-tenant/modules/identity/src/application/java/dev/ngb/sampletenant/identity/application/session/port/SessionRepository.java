package dev.ngb.sampletenant.identity.application.session.port;

import dev.ngb.sampletenant.identity.domain.session.UserSession;
import java.time.Instant;
import java.util.Optional;

/** Persistence boundary for opaque access tokens. Only their digests are stored. */
public interface SessionRepository {

    void save(UserSession session);

    Optional<UserSession> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);

    void deleteExpired(Instant now);
}
