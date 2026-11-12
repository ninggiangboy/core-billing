package dev.ngb.tenant.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.tenant.identity.api.security.Role;
import dev.ngb.tenant.support.HttpIntegrationTest;
import dev.ngb.tenant.support.HttpWorld;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class UserHttpIntegrationTest extends HttpIntegrationTest {

    @Test
    void administersAccounts() {
        String admin = world.adminToken();

        MvcResult created = world.call(HttpMethod.POST, admin, "/api/users", HttpWorld.map(
                "email", "operator-" + UUID.randomUUID() + "@example.com",
                "displayName", "Operator",
                "password", HttpWorld.PASSWORD,
                "role", "OPERATOR"));
        world.requireStatus(created, 201);
        var createdBody = world.parse(created);
        String id = createdBody.get("id").asString();
        assertThat(createdBody.get("role").asString()).isEqualTo("OPERATOR");

        var list = world.body(world.call(HttpMethod.GET, admin, "/api/users", null));
        assertThat(list.isArray()).isTrue();
        assertThat(list.findValuesAsString("id")).contains(id);

        var byId = world.body(world.call(HttpMethod.GET, admin, "/api/users/" + id, null));
        assertThat(byId.get("id").asString()).isEqualTo(id);

        var changed = world.body(world.call(HttpMethod.PUT, admin, "/api/users/" + id + "/role", HttpWorld.map("role", "ADMIN")));
        assertThat(changed.get("role").asString()).isEqualTo("ADMIN");

        var deactivated = world.body(world.call(HttpMethod.POST, admin, "/api/users/" + id + "/deactivate", null));
        assertThat(deactivated.get("status").asString()).isEqualTo("DISABLED");
    }

    @Test
    void aViewerCannotAdministrateAccounts() {
        String viewer = world.token(Role.VIEWER);

        world.requireStatus(world.call(HttpMethod.GET, viewer, "/api/users", null), 403);
    }

    @Test
    void anUnknownAccountIsNotFound() {
        String admin = world.adminToken();

        world.requireStatus(world.call(HttpMethod.GET, admin, "/api/users/" + UUID.randomUUID(), null), 404);
    }
}
