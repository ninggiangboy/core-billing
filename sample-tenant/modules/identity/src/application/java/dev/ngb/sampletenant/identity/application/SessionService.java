package dev.ngb.sampletenant.identity.application;

import dev.ngb.core.runtime.Clock;
import dev.ngb.sampletenant.identity.application.port.SessionRepository;
import dev.ngb.sampletenant.identity.application.port.UserRepository;
import dev.ngb.sampletenant.identity.application.security.Tokens;
import dev.ngb.sampletenant.identity.domain.User;
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
