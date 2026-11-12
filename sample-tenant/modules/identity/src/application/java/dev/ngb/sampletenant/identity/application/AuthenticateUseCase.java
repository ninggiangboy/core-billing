package dev.ngb.sampletenant.identity.application;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.application.port.PasswordHasher;
import dev.ngb.sampletenant.identity.application.port.SessionRepository;
import dev.ngb.sampletenant.identity.application.port.UserRepository;
import dev.ngb.sampletenant.identity.application.security.Tokens;
import dev.ngb.sampletenant.identity.domain.User;
import dev.ngb.sampletenant.identity.domain.UserSession;
import dev.ngb.sampletenant.shared.error.ForbiddenException;
import dev.ngb.sampletenant.shared.error.UnauthorizedException;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/** Verifies credentials and opens an opaque session. */
public class AuthenticateUseCase {

    public record Result(String token, Instant expiresAt, User user) {}

    private final UserRepository users;
    private final SessionRepository sessions;
    private final PasswordHasher hasher;
    private final Clock clock;
    private final IdGenerator ids;
    private final Duration sessionTtl;

    public AuthenticateUseCase(
            UserRepository users,
            SessionRepository sessions,
            PasswordHasher hasher,
            Clock clock,
            IdGenerator ids,
            Duration sessionTtl) {
        this.users = users;
        this.sessions = sessions;
        this.hasher = hasher;
        this.clock = clock;
        this.ids = ids;
        this.sessionTtl = sessionTtl;
    }

    public Result execute(String email, String password) {
        String normalized = email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
        User user = users.findByEmail(normalized).orElseThrow(() -> new UnauthorizedException("invalid email or password"));
        if (!user.active()) {
            throw new ForbiddenException("this account is disabled");
        }
        if (password == null || !hasher.matches(password, user.passwordHash())) {
            throw new UnauthorizedException("invalid email or password");
        }
        Instant now = clock.now();
        String token = Tokens.generate();
        Instant expiresAt = now.plus(sessionTtl);
        sessions.save(UserSession.open(ids, user.id(), Tokens.digest(token), now, expiresAt));
        return new Result(token, expiresAt, user);
    }
}
