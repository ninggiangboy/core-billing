package dev.ngb.sampletenant.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.sampletenant.identity.api.Role;
import dev.ngb.sampletenant.identity.domain.User;
import dev.ngb.sampletenant.shared.error.ForbiddenException;
import dev.ngb.sampletenant.shared.error.UnauthorizedException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.Test;

class AuthenticateUseCaseTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    private final Fakes.Users users = new Fakes.Users();
    private final Fakes.Sessions sessions = new Fakes.Sessions();
    private final AuthenticateUseCase authenticate = new AuthenticateUseCase(
            users, sessions, new Fakes.Hasher(), Clock.fixed(NOW, ZoneOffset.UTC), Duration.ofHours(12));

    private User account() {
        User user = User.register("ada@example.com", "Ada", "hash:correct-horse", Role.ADMIN, NOW);
        users.save(user);
        return user;
    }

    @Test
    void opensAnOpaqueSessionWithoutStoringTheToken() {
        account();

        AuthenticateUseCase.Result result = authenticate.execute("ada@example.com", "correct-horse");

        assertThat(result.token()).isNotBlank().doesNotContain("hash");
        assertThat(result.expiresAt()).isEqualTo(NOW.plus(Duration.ofHours(12)));
        assertThat(sessions.size()).isEqualTo(1);
        assertThat(sessions.findByTokenHash(result.token())).isEmpty();
    }

    @Test
    void refusesAWrongPassword() {
        account();

        assertThatThrownBy(() -> authenticate.execute("ada@example.com", "wrong-password"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refusesAnUnknownEmailWithTheSameAnswer() {
        assertThatThrownBy(() -> authenticate.execute("nobody@example.com", "correct-horse"))
                .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refusesADisabledAccount() {
        User user = account();
        users.save(user.disabled());

        assertThatThrownBy(() -> authenticate.execute("ada@example.com", "correct-horse"))
                .isInstanceOf(ForbiddenException.class);
    }
}
