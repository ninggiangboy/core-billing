package dev.ngb.sampletenant.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.session.port.SessionRepository;
import dev.ngb.sampletenant.identity.application.user.command.RegisterUserUseCase;
import dev.ngb.sampletenant.identity.domain.session.UserSession;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqSessionRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    SessionRepository sessions;

    @Autowired
    IdGenerator ids;

    private UUID userId() {
        String email = "session-" + UUID.randomUUID() + "@example.com";
        User user = registerUser.execute(
                new RegisterUserUseCase.Command(email, "Ada", HttpWorld.PASSWORD, Role.VIEWER));
        return user.id();
    }

    @Test
    void roundTripsAndDeletesASession() {
        String hash = "hash-" + UUID.randomUUID();
        UserSession session = UserSession.open(ids, userId(), hash, NOW, NOW.plusSeconds(60));

        sessions.save(session);

        UserSession loaded = sessions.findByTokenHash(hash).orElseThrow();
        assertThat(loaded.id()).isEqualTo(session.id());
        assertThat(loaded.expiresAt()).isEqualTo(session.expiresAt());

        sessions.deleteByTokenHash(hash);
        assertThat(sessions.findByTokenHash(hash)).isEmpty();
    }

    @Test
    void savingAnExistingSessionOnlyMovesItsExpiry() {
        String hash = "hash-" + UUID.randomUUID();
        UUID userId = userId();
        UserSession session = UserSession.open(ids, userId, hash, NOW, NOW.plusSeconds(60));
        sessions.save(session);

        sessions.save(new UserSession(session.id(), userId(), "other-" + hash, NOW.plusSeconds(5), NOW.plusSeconds(600)));

        UserSession loaded = sessions.findByTokenHash(hash).orElseThrow();
        assertThat(loaded.id()).isEqualTo(session.id());
        assertThat(loaded.userId()).isEqualTo(userId);
        assertThat(loaded.createdAt()).isEqualTo(NOW);
        assertThat(loaded.expiresAt()).isEqualTo(NOW.plusSeconds(600));
        assertThat(sessions.findByTokenHash("other-" + hash)).isEmpty();
    }

    @Test
    void deleteExpiredRemovesOnlyPastSessions() {
        String expiredHash = "hash-" + UUID.randomUUID();
        String activeHash = "hash-" + UUID.randomUUID();
        UUID userId = userId();
        sessions.save(UserSession.open(ids, userId, expiredHash, NOW.minusSeconds(120), NOW.minusSeconds(1)));
        sessions.save(UserSession.open(ids, userId, activeHash, NOW, NOW.plusSeconds(60)));

        sessions.deleteExpired(NOW);

        assertThat(sessions.findByTokenHash(expiredHash)).isEmpty();
        assertThat(sessions.findByTokenHash(activeHash)).isPresent();
    }
}
