package dev.ngb.sampletenant.usage;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.identity.api.security.Role;
import dev.ngb.sampletenant.support.HttpIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;

class UsageHttpIntegrationTest extends HttpIntegrationTest {

    @Test
    void listsTheCurrentCustomersUsage() {
        String viewer = world.token(Role.VIEWER);

        var usage = world.body(world.call(HttpMethod.GET, viewer, "/api/usage", null));

        assertThat(usage.isArray()).isTrue();
        assertThat(usage.size()).isZero();
    }

    @Test
    void requiresAuthentication() {
        world.requireStatus(world.call(HttpMethod.GET, null, "/api/usage", null), 401);
    }
}
