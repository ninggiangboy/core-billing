package dev.ngb.sampletenant.identity;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.sampletenant.support.HttpIntegrationTest;
import dev.ngb.sampletenant.support.HttpWorld;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MvcResult;

class AuthHttpIntegrationTest extends HttpIntegrationTest {

    @Test
    void registersAViewerAndReturnsTheAccount() {
        world.adminToken();

        MvcResult result = world.call(HttpMethod.POST, null, "/api/auth/register", HttpWorld.map(
                "email", "ada-" + System.nanoTime() + "@example.com",
                "displayName", "Ada",
                "password", HttpWorld.PASSWORD));

        world.requireStatus(result, 201);
        var body = world.parse(result);
        assertThat(body.get("id").asString()).isNotBlank();
        assertThat(body.get("email").asString()).startsWith("ada-");
        assertThat(body.get("displayName").asString()).isEqualTo("Ada");
        assertThat(body.get("role").asString()).isEqualTo("VIEWER");
        assertThat(body.get("status").asString()).isEqualTo("ACTIVE");
        assertThat(body.get("createdAt").asString()).isNotBlank();
    }

    @Test
    void logsInAndServesTheAuthenticatedAccount() {
        HttpWorld.Account account = world.registerViaApi();

        var me = world.body(world.call(HttpMethod.GET, account.token(), "/api/users/me", null));

        assertThat(me.get("id").asString()).isEqualTo(account.id());
        assertThat(me.get("email").asString()).isEqualTo(account.email());
        assertThat(me.get("role").asString()).isEqualTo("VIEWER");
    }

    @Test
    void refusesAWrongPassword() {
        HttpWorld.Account account = world.registerViaApi();

        MvcResult result = world.call(HttpMethod.POST, null, "/api/auth/login", HttpWorld.map(
                "email", account.email(), "password", "wrong-password"));

        world.requireStatus(result, 401);
        assertThat(world.parse(result).get("detail").asString()).isEqualTo("invalid email or password");
    }

    @Test
    void refusesADuplicateEmail() {
        HttpWorld.Account account = world.registerViaApi();

        MvcResult result = world.call(HttpMethod.POST, null, "/api/auth/register", HttpWorld.map(
                "email", account.email(), "displayName", "Ada again", "password", HttpWorld.PASSWORD));

        world.requireStatus(result, 409);
        assertThat(world.parse(result).get("detail").asString()).isEqualTo("an account with this email already exists");
    }

    @Test
    void logoutInvalidatesTheSession() {
        HttpWorld.Account account = world.registerViaApi();

        world.requireStatus(world.call(HttpMethod.POST, account.token(), "/api/auth/logout", null), 204);
        world.requireStatus(world.call(HttpMethod.GET, account.token(), "/api/users/me", null), 401);
    }

    @Test
    void aMissingTokenIsUnauthorized() {
        world.requireStatus(world.call(HttpMethod.GET, null, "/api/users/me", null), 401);
    }
}
