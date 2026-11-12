package dev.ngb.tenant.identity.domain.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.shared.error.BadRequestException;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UserTest {

    private static final UUID ID = UUID.fromString("00000000-0000-7000-8000-000000000001");
    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");
    private static final IdGenerator IDS = () -> ID;

    @Test
    void registeringNormalisesTheEmailAndNameAndStartsActive() {
        User user = User.register(IDS, "  Ada@Example.COM ", "  Ada  ", "hash", Role.VIEWER, NOW);

        assertThat(user.id()).isEqualTo(ID);
        assertThat(user.email()).isEqualTo("ada@example.com");
        assertThat(user.displayName()).isEqualTo("Ada");
        assertThat(user.passwordHash()).isEqualTo("hash");
        assertThat(user.role()).isEqualTo(Role.VIEWER);
        assertThat(user.status()).isEqualTo(UserStatus.ACTIVE);
        assertThat(user.createdAt()).isEqualTo(NOW);
        assertThat(user.active()).isTrue();
    }

    @Test
    void rejectsAnEmailThatIsNotAnAddress() {
        assertThatThrownBy(() -> User.register(IDS, "not-an-email", "Ada", "hash", Role.VIEWER, NOW))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> User.register(IDS, "a b@example.com", "Ada", "hash", Role.VIEWER, NOW))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> User.register(IDS, null, "Ada", "hash", Role.VIEWER, NOW))
                .isInstanceOf(BadRequestException.class);
    }

    @Test
    void rejectsABlankOrMissingDisplayName() {
        assertThatThrownBy(() -> User.register(IDS, "ada@example.com", "   ", "hash", Role.VIEWER, NOW))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> User.register(IDS, "ada@example.com", null, "hash", Role.VIEWER, NOW))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("a display name is required");
    }

    @Test
    void changingTheRoleKeepsTheRest() {
        User user = User.register(IDS, "ada@example.com", "Ada", "hash", Role.VIEWER, NOW);

        User admin = user.withRole(Role.ADMIN);

        assertThat(admin.role()).isEqualTo(Role.ADMIN);
        assertThat(admin).usingRecursiveComparison().ignoringFields("role").isEqualTo(user);
    }

    @Test
    void disablingMakesTheAccountInactive() {
        User disabled = User.register(IDS, "ada@example.com", "Ada", "hash", Role.VIEWER, NOW).disabled();

        assertThat(disabled.status()).isEqualTo(UserStatus.DISABLED);
        assertThat(disabled.active()).isFalse();
    }
}
