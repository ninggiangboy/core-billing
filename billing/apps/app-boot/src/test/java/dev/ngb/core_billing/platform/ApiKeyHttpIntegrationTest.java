package dev.ngb.core_billing.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;

/** Tenant System credentials over HTTP: issue, use, rotate, revoke, and never leak the secret. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@ExtendWith(OutputCaptureExtension.class)
class ApiKeyHttpIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    HttpWorld world;
    Account acme;
    Account globex;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
    }

    private String newSystem(Account account) {
        return world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", "System " + System.nanoTime()))).get("id").asString();
    }

    private JsonNode newCredential(Account account, String systemId) {
        return world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "ci", "scopes", List.of("customer:read", "customer:write"))));
    }

    @Test
    void aTenantSystemAuthenticatesWithItsKeyAndNothingMore(CapturedOutput output) throws Exception {
        String systemId = newSystem(acme);
        JsonNode issued = newCredential(acme, systemId);
        String key = issued.get("key").asString();
        String secret = key.substring(key.lastIndexOf('_') + 1);

        JsonNode me = world.body(world.call(HttpMethod.GET, acme.host(), key, "/api/v1/me", null));
        assertThat(me.get("type").asString()).isEqualTo("TENANT_SYSTEM");
        assertThat(me.get("tenantId").asString()).isEqualTo(acme.tenantId());
        assertThat(me.get("permissions").toString()).contains("customer:read", "customer:write").doesNotContain("user:");

        assertThat(world.status(HttpMethod.GET, acme.host(), key, "/api/v1/users", null)).as("admin endpoint with a machine key").isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), key, "/api/v1/tenant-systems/" + systemId + "/credentials/rotate", null)).isEqualTo(403);

        // The secret appears once, in the create response, and nowhere else.
        String listing = world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials", null)
                .getResponse().getContentAsString();
        assertThat(listing).doesNotContain(secret).doesNotContain("key\"");
        assertThat(output.getAll()).as("application log output").doesNotContain(secret);
    }

    @Test
    void aKeyDoesNotWorkOnAnotherTenantOrWhenWrong() {
        JsonNode issued = newCredential(acme, newSystem(acme));
        String key = issued.get("key").asString();

        assertThat(world.status(HttpMethod.GET, globex.host(), key, "/api/v1/me", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, world.platformHost(), key, "/api/v1/me", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, acme.host(), key.substring(0, key.length() - 1) + (key.endsWith("0") ? "1" : "0"),
                "/api/v1/me", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, acme.host(), "cbk_nonsense", "/api/v1/me", null)).isEqualTo(401);
    }

    @Test
    void revocationTakesEffectOnTheNextCall() {
        String systemId = newSystem(acme);
        JsonNode issued = newCredential(acme, systemId);
        String key = issued.get("key").asString();
        assertThat(world.status(HttpMethod.GET, acme.host(), key, "/api/v1/me", null)).isEqualTo(200);

        JsonNode revoked = world.body(world.call(HttpMethod.DELETE, acme.host(), acme.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials/" + issued.get("credential").get("id").asString(), null));

        assertThat(revoked.get("status").asString()).isEqualTo("REVOKED");
        assertThat(world.status(HttpMethod.GET, acme.host(), key, "/api/v1/me", null)).isEqualTo(401);
    }

    @Test
    void rotationOverlapsThenTheOldKeyCanBeRevoked() {
        String systemId = newSystem(acme);
        JsonNode first = newCredential(acme, systemId);
        String oldKey = first.get("key").asString();

        JsonNode second = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials/rotate", HttpWorld.Map2.of("overlapMinutes", 30)));
        String newKey = second.get("key").asString();

        assertThat(newKey).isNotEqualTo(oldKey);
        assertThat(second.get("credential").get("scopes").toString()).contains("customer:read");
        assertThat(world.status(HttpMethod.GET, acme.host(), oldKey, "/api/v1/me", null)).as("old key during overlap").isEqualTo(200);
        assertThat(world.status(HttpMethod.GET, acme.host(), newKey, "/api/v1/me", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials/rotate", null)).as("second rotation inside the overlap").isEqualTo(409);

        world.body(world.call(HttpMethod.DELETE, acme.host(), acme.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials/" + first.get("credential").get("id").asString(), null));
        assertThat(world.status(HttpMethod.GET, acme.host(), oldKey, "/api/v1/me", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, acme.host(), newKey, "/api/v1/me", null)).isEqualTo(200);
    }

    @Test
    void invalidRequestsGetClearClientErrors() {
        String systemId = newSystem(acme);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", List.of("user:manage")))).as("scope a machine may not hold").isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/" + systemId + "/credentials",
                HttpWorld.Map2.of("label", "x", "scopes", List.of("nope:nope")))).isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(),
                "/api/v1/tenant-systems/00000000-0000-0000-0000-000000000000/credentials", HttpWorld.Map2.of("label", "x", "scopes", Set.of()))).isEqualTo(404);
        assertThat(world.status(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/tenant-systems/not-a-uuid", null)).isEqualTo(400);
        assertThat(world.status(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", ""))).isEqualTo(400);
    }

    @Test
    void aTenantCannotSeeAnotherTenantsSystems() {
        String systemId = newSystem(acme);
        assertThat(world.status(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/tenant-systems/" + systemId, null)).isEqualTo(404);
        assertThat(world.status(HttpMethod.POST, globex.host(), globex.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials", HttpWorld.Map2.of("label", "x", "scopes", Set.of()))).isEqualTo(404);
    }
}
