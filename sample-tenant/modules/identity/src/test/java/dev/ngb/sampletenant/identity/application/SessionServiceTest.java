package dev.ngb.sampletenant.identity.application;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.identity.api.Role;
import dev.ngb.sampletenant.identity.application.security.Tokens;
import dev.ngb.sampletenant.identity.domain.User;
import dev.ngb.sampletenant.identity.domain.UserSession;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class SessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    private final Fakes.Users users = new Fakes.Users();
    private final Fakes.Sessions sessions = new Fakes.Sessions();
    private final SessionService service = new SessionService(users, sessions, Clock.fixed(NOW, ZoneOffset.UTC));

    @Test
    void resolvesAnActiveSession() {
        User user = User.register("ada@example.com", "Ada", "hash", Role.ADMIN, NOW);
        users.save(user);
        sessions.save(UserSession.open(user.id(), Tokens.digest("token"), NOW, NOW.plusSeconds(60)));

        assertThat(service.resolve("token")).contains(user);
    }

    @Test
    void ignoresAnExpiredSession() {
        User user = User.register("ada@example.com", "Ada", "hash", Role.ADMIN, NOW.minusSeconds(120));
        users.save(user);
        sessions.save(UserSession.open(user.id(), Tokens.digest("token"), NOW.minusSeconds(120), NOW.minusSeconds(1)));

        assertThat(service.resolve("token")).isEmpty();
    }

    @Test
    void logoutRemovesOnlyThatSession() {
        User user = User.register("ada@example.com", "Ada", "hash", Role.ADMIN, NOW);
        users.save(user);
        sessions.save(UserSession.open(user.id(), Tokens.digest("token"), NOW, NOW.plusSeconds(60)));

        service.logout("token");

        assertThat(service.resolve("token")).isEmpty();
        assertThat(sessions.size()).isZero();
    }

    @Test
    void purgeRemovesExpiredSessions() {
        User user = User.register("ada@example.com", "Ada", "hash", Role.ADMIN, NOW);
        users.save(user);
        sessions.save(UserSession.open(user.id(), Tokens.digest("old"), NOW.minusSeconds(120), NOW.minusSeconds(1)));
        sessions.save(UserSession.open(user.id(), Tokens.digest("new"), NOW, NOW.plusSeconds(60)));

        service.purgeExpired();

        assertThat(sessions.size()).isEqualTo(1);
    }
}
