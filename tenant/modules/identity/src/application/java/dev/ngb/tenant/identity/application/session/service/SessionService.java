package dev.ngb.tenant.identity.application.session.service;

import dev.ngb.core.runtime.Clock;
import dev.ngb.tenant.identity.application.session.port.SessionRepository;
import dev.ngb.tenant.identity.application.user.port.UserRepository;
import dev.ngb.tenant.identity.application.session.service.Tokens;
import dev.ngb.tenant.identity.domain.user.User;
import java.util.Optional;

/** Resolves a bearer token to the active account that opened the session. */
public class SessionService {

    private final UserRepository users;
    private final SessionRepository sessions;
    private final Clock clock;

    public SessionService(UserRepository users, SessionRepository sessions, Clock clock) {
        this.users = users;
        this.sessions = sessions;
        this.clock = clock;
    }

    public Optional<User> resolve(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        return sessions.findByTokenHash(Tokens.digest(token))
                .filter(session -> !session.expired(clock.now()))
                .flatMap(session -> users.findById(session.userId()))
                .filter(User::active);
    }

    public void logout(String token) {
        if (token != null && !token.isBlank()) {
            sessions.deleteByTokenHash(Tokens.digest(token));
        }
    }

    public void purgeExpired() {
        sessions.deleteExpired(clock.now());
    }
}
