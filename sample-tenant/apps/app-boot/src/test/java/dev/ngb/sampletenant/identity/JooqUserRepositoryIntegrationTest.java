package dev.ngb.sampletenant.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.identity.application.user.port.UserRepository;
import dev.ngb.sampletenant.identity.application.user.query.UserReadRepository;
import dev.ngb.sampletenant.identity.domain.user.User;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

class JooqUserRepositoryIntegrationTest extends HttpIntegrationTest {

    private static final Instant NOW = Instant.parse("2026-11-11T00:00:00Z");

    @Autowired
    UserRepository users;

    @Autowired
    UserReadRepository read;

    @Autowired
    IdGenerator ids;

    private User account() {
        return User.register(
                ids, "person-" + UUID.randomUUID() + "@example.com", "Ada", "hash", Role.VIEWER, NOW);
    }

    @Test
    void roundTripsAndUpsertsAnAccount() {
        User user = account();

        users.save(user);

        assertThat(users.findById(user.id()).orElseThrow().email()).isEqualTo(user.email());
        assertThat(users.findByEmail(user.email())).isPresent();
        assertThat(users.emailExists(user.email())).isTrue();
        assertThat(users.anyExists()).isTrue();

        users.save(user.withRole(Role.ADMIN));

        assertThat(users.findById(user.id()).orElseThrow().role()).isEqualTo(Role.ADMIN);
    }

    @Test
    void theReadSideSelectsIntoViews() {
        User user = account();
        users.save(user);

        assertThat(read.byId(user.id()).orElseThrow().role()).isEqualTo(Role.VIEWER);
        assertThat(read.list()).extracting(view -> view.id()).contains(user.id());
    }
}
