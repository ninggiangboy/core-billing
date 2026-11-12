package dev.ngb.tenant.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.identity.application.session.service.SessionService;
import dev.ngb.tenant.identity.application.session.service.Tokens;
import dev.ngb.tenant.identity.domain.user.User;
import dev.ngb.tenant.identity.domain.session.UserSession;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    private final Fakes.Users users = new Fakes.Users();
    private final Fakes.Sessions sessions = new Fakes.Sessions();
    private final IdGenerator ids = () -> UUID.randomUUID();
    private final SessionService service = new SessionService(users, sessions, () -> NOW);

    @Test
    void resolvesAnActiveSession() {
        User user = User.register(ids, "ada@example.com", "Ada", "hash", Role.ADMIN, NOW);
        users.save(user);
        sessions.save(UserSession.open(ids, user.id(), Tokens.digest("token"), NOW, NOW.plusSeconds(60)));

        assertThat(service.resolve("token")).contains(user);
    }

    @Test
    void ignoresAnExpiredSession() {
        User user = User.register(ids, "ada@example.com", "Ada", "hash", Role.ADMIN, NOW.minusSeconds(120));
        users.save(user);
        sessions.save(UserSession.open(ids, user.id(), Tokens.digest("token"), NOW.minusSeconds(120), NOW.minusSeconds(1)));

        assertThat(service.resolve("token")).isEmpty();
    }

    @Test
    void logoutRemovesOnlyThatSession() {
        User user = User.register(ids, "ada@example.com", "Ada", "hash", Role.ADMIN, NOW);
        users.save(user);
        sessions.save(UserSession.open(ids, user.id(), Tokens.digest("token"), NOW, NOW.plusSeconds(60)));

        service.logout("token");

        assertThat(service.resolve("token")).isEmpty();
        assertThat(sessions.size()).isZero();
    }

    @Test
    void purgeRemovesExpiredSessions() {
        User user = User.register(ids, "ada@example.com", "Ada", "hash", Role.ADMIN, NOW);
        users.save(user);
        sessions.save(UserSession.open(ids, user.id(), Tokens.digest("old"), NOW.minusSeconds(120), NOW.minusSeconds(1)));
        sessions.save(UserSession.open(ids, user.id(), Tokens.digest("new"), NOW, NOW.plusSeconds(60)));

        service.purgeExpired();

        assertThat(sessions.size()).isEqualTo(1);
    }
}
