package dev.ngb.sampletenant.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.api.Role;
import dev.ngb.sampletenant.identity.api.account.AccountListener;
import dev.ngb.sampletenant.identity.api.account.RegisteredAccount;
import dev.ngb.sampletenant.identity.domain.User;
import dev.ngb.sampletenant.shared.error.BadRequestException;
import dev.ngb.sampletenant.shared.error.ConflictException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RegisterUserUseCaseTest {

    private static final AccountListener NO_LISTENER = new AccountListener() {
        @Override
        public void registered(RegisteredAccount account) {}
    };

    private final Fakes.Users users = new Fakes.Users();
    private final RegisterUserUseCase register = new RegisterUserUseCase(
            users,
            new Fakes.Hasher(),
            () -> Instant.parse("2026-11-11T00:00:00Z"),
            () -> UUID.randomUUID(),
            NO_LISTENER);

    @Test
    void registersAnActiveUserWithANormalizedEmailAndHashedPassword() {
        User user = register.execute(new RegisterUserUseCase.Command(" Ada@Example.COM ", " Ada ", "correct-horse", Role.VIEWER));

        assertThat(user.email()).isEqualTo("ada@example.com");
        assertThat(user.displayName()).isEqualTo("Ada");
        assertThat(user.passwordHash()).isEqualTo("hash:correct-horse");
        assertThat(user.active()).isTrue();
        assertThat(register.anyUserExists()).isTrue();
    }

    @Test
    void refusesADuplicateEmail() {
        register.execute(new RegisterUserUseCase.Command("ada@example.com", "Ada", "correct-horse", Role.VIEWER));

        assertThatThrownBy(() -> register.execute(
                        new RegisterUserUseCase.Command("ADA@example.com", "Ada again", "another-password", Role.VIEWER)))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void refusesAShortPassword() {
        assertThatThrownBy(() -> register.execute(
                        new RegisterUserUseCase.Command("ada@example.com", "Ada", "short", Role.VIEWER)))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void refusesAnInvalidEmail() {
        assertThatThrownBy(() -> register.execute(
                        new RegisterUserUseCase.Command("not-an-email", "Ada", "correct-horse", Role.VIEWER)))
                .isInstanceOf(BadRequestException.class);
    }
}
