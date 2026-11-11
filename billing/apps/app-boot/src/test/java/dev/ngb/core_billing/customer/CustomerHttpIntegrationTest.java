package dev.ngb.core_billing.customer;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.core_billing.support.HttpWorld;
import dev.ngb.core_billing.support.HttpWorld.Account;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import dev.ngb.core_billing.support.RealKeycloakConfig;
import dev.ngb.core_billing.support.ScopeTestSupport;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.http.MediaType;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** Customers over HTTP, for Tenant Systems (API key) and Tenant Users (OIDC), with the idempotency contract. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class CustomerHttpIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    private final JsonMapper json = JsonMapper.builder().build();
    HttpWorld world;
    Account acme;
    Account globex;
    String acmeSystemId;
    String acmeKey;
    String acmeReadOnlyKey;
    String globexKey;

    @BeforeAll
    void createWorld() {
        world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        acme = world.newTenant();
        globex = world.newTenant();
        acmeSystemId = newSystem(acme);
        acmeKey = newKey(acme, acmeSystemId, List.of("customer:read", "customer:write"));
        acmeReadOnlyKey = newKey(acme, acmeSystemId, List.of("customer:read"));
        globexKey = newKey(globex, newSystem(globex), List.of("customer:read", "customer:write"));
    }

    private String newSystem(Account account) {
        return world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", "System " + UUID.randomUUID()))).get("id").asString();
    }

    private String newKey(Account account, String systemId, List<String> scopes) {
        return world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials", HttpWorld.Map2.of("label", "x", "scopes", scopes))).get("key").asString();
    }

    private MvcResult post(Account account, String token, String idempotencyKey, Object body) throws Exception {
        var request = MockMvcRequestBuilders.post("/api/v1/customers").header("Host", account.host())
                .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(body));
        if (token != null) {
            request.header("Authorization", "Bearer " + token);
        }
        if (idempotencyKey != null) {
            request.header("Idempotency-Key", idempotencyKey);
        }
        return mvc.perform(request).andReturn();
    }

    private JsonNode parse(MvcResult result) throws Exception {
        return json.readTree(result.getResponse().getContentAsString());
    }

    @Test
    void aTenantSystemCreatesAMappedCustomerAndRetriesSafely() throws Exception {
        String externalUser = "user_" + UUID.randomUUID();
        Map<String, Object> body = HttpWorld.Map2.of("externalUserId", externalUser, "displayName", "Acme Ltd", "email", "billing@acme.test");
        String key = "create-" + UUID.randomUUID();

        MvcResult first = post(acme, acmeKey, key, body);
        MvcResult retry = post(acme, acmeKey, key, body);

        assertThat(first.getResponse().getStatus()).isEqualTo(201);
        assertThat(retry.getResponse().getStatus()).isEqualTo(200);
        assertThat(parse(retry).get("id").asString()).isEqualTo(parse(first).get("id").asString());
        assertThat(parse(first).get("identities").get(0).get("tenantSystemId").asString()).isEqualTo(acmeSystemId);
        assertThat(parse(first).get("identities").get(0).get("externalUserId").asString()).isEqualTo(externalUser);

        MvcResult other = post(acme, acmeKey, key, HttpWorld.Map2.of("externalUserId", externalUser, "displayName", "Different"));
        assertThat(other.getResponse().getStatus()).as("same key, different request").isEqualTo(409);
        MvcResult duplicateUser = post(acme, acmeKey, "another-" + UUID.randomUUID(), body);
        assertThat(duplicateUser.getResponse().getStatus()).as("same external user, new key").isEqualTo(409);
    }

    @Test
    void theIdempotencyKeyIsRequired() throws Exception {
        assertThat(post(acme, acmeKey, null, HttpWorld.Map2.of("externalUserId", "u", "displayName", "No key")).getResponse().getStatus()).isEqualTo(400);
        assertThat(post(acme, acmeKey, "has space", HttpWorld.Map2.of("externalUserId", "u", "displayName", "Bad key")).getResponse().getStatus()).isEqualTo(400);
        assertThat(post(acme, null, "k", HttpWorld.Map2.of("displayName", "x")).getResponse().getStatus()).isEqualTo(401);
    }

    @Test
    void aTenantSystemFindsItsCustomerByExternalUserAndNobodyElseDoes() throws Exception {
        String externalUser = "user_" + UUID.randomUUID();
        String id = parse(post(acme, acmeKey, "k-" + UUID.randomUUID(), HttpWorld.Map2.of("externalUserId", externalUser, "displayName", "Findable"))).get("id").asString();

        var found = world.body(world.call(HttpMethod.GET, acme.host(), acmeKey, "/api/v1/customers/by-external-user/" + externalUser, null));
        assertThat(found.get("id").asString()).isEqualTo(id);
        assertThat(world.status(HttpMethod.GET, acme.host(), acmeKey, "/api/v1/customers/by-external-user/nobody", null)).isEqualTo(404);

        // The same external id in another tenant is a different, independent customer (or none at all).
        assertThat(world.status(HttpMethod.GET, globex.host(), globexKey, "/api/v1/customers/by-external-user/" + externalUser, null)).isEqualTo(404);
        assertThat(world.status(HttpMethod.GET, globex.host(), globexKey, "/api/v1/customers/" + id, null)).isEqualTo(404);
        assertThat(world.status(HttpMethod.GET, globex.host(), globex.adminToken(), "/api/v1/customers/" + id, null)).isEqualTo(404);

        String inGlobex = parse(post(globex, globexKey, "k-" + UUID.randomUUID(), HttpWorld.Map2.of("externalUserId", externalUser, "displayName", "Globex's"))).get("id").asString();
        assertThat(inGlobex).isNotEqualTo(id);
    }

    @Test
    void aReadOnlyKeyReadsButCannotWrite() throws Exception {
        assertThat(post(acme, acmeReadOnlyKey, "k-" + UUID.randomUUID(), HttpWorld.Map2.of("externalUserId", "u", "displayName", "x")).getResponse().getStatus()).isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), acmeReadOnlyKey, "/api/v1/customers", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.PUT, acme.host(), acmeReadOnlyKey, "/api/v1/customers/" + UUID.randomUUID(), HttpWorld.Map2.of("displayName", "x"))).isEqualTo(403);
    }

    @Test
    void aTenantSystemCannotActForAnotherSystem() throws Exception {
        String otherSystem = newSystem(acme);
        MvcResult result = post(acme, acmeKey, "k-" + UUID.randomUUID(),
                HttpWorld.Map2.of("tenantSystemId", otherSystem, "externalUserId", "u", "displayName", "x"));
        assertThat(result.getResponse().getStatus()).isEqualTo(403);
    }

    @Test
    void aTenantUserAdministersCustomersWithTheRightPermissions() throws Exception {
        String viewerRole = viewerRoleId(acme);
        String viewer = world.newUserToken(acme, Set.of(viewerRole));
        String nobody = world.newUserToken(acme, Set.of());

        MvcResult created = post(acme, acme.adminToken(), "admin-" + UUID.randomUUID(),
                HttpWorld.Map2.of("tenantSystemId", acmeSystemId, "externalUserId", "u-" + UUID.randomUUID(), "displayName", "By admin", "email", "a@b.test"));
        assertThat(created.getResponse().getStatus()).isEqualTo(201);
        String id = parse(created).get("id").asString();

        JsonNode updated = world.body(world.call(HttpMethod.PUT, acme.host(), acme.adminToken(), "/api/v1/customers/" + id,
                HttpWorld.Map2.of("displayName", "Renamed")));
        assertThat(updated.get("displayName").asString()).isEqualTo("Renamed");
        assertThat(updated.get("identities").size()).isEqualTo(1);
        assertThat(world.body(world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/customers", null)).toString()).contains(id);

        assertThat(world.status(HttpMethod.GET, acme.host(), viewer, "/api/v1/customers/" + id, null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.PUT, acme.host(), viewer, "/api/v1/customers/" + id, HttpWorld.Map2.of("displayName", "no"))).isEqualTo(403);
        assertThat(world.status(HttpMethod.POST, acme.host(), viewer, "/api/v1/customers/" + id + "/deactivate", null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), nobody, "/api/v1/customers", null)).isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), nobody, "/api/v1/customers/by-external-user/x", null)).isEqualTo(403);

        JsonNode deactivated = world.body(world.call(HttpMethod.POST, acme.host(), acme.adminToken(), "/api/v1/customers/" + id + "/deactivate", null));
        assertThat(deactivated.get("status").asString()).isEqualTo("DEACTIVATED");
        assertThat(world.status(HttpMethod.PUT, acme.host(), acme.adminToken(), "/api/v1/customers/" + id, HttpWorld.Map2.of("displayName", "late"))).isEqualTo(409);
    }

    @Test
    void aTenantUserMayCreateAnUnmappedCustomerButNotAHalfMappedOne() throws Exception {
        MvcResult unmapped = post(acme, acme.adminToken(), "u-" + UUID.randomUUID(), HttpWorld.Map2.of("displayName", "Walk-in"));
        assertThat(unmapped.getResponse().getStatus()).isEqualTo(201);
        assertThat(parse(unmapped).get("identities").size()).isZero();

        MvcResult half = post(acme, acme.adminToken(), "h-" + UUID.randomUUID(), HttpWorld.Map2.of("externalUserId", "x", "displayName", "Half"));
        assertThat(half.getResponse().getStatus()).isEqualTo(400);
        MvcResult foreignSystem = post(acme, acme.adminToken(), "f-" + UUID.randomUUID(),
                HttpWorld.Map2.of("tenantSystemId", UUID.randomUUID().toString(), "externalUserId", "x", "displayName", "Ghost system"));
        assertThat(foreignSystem.getResponse().getStatus()).as("a tenant system that does not exist").isEqualTo(409);
    }

    private String viewerRoleId(Account account) {
        JsonNode roles = world.body(world.call(HttpMethod.GET, account.host(), account.adminToken(), "/api/v1/roles", null));
        for (JsonNode role : roles) {
            if (role.get("name").asString().equals("Tenant Viewer")) {
                return role.get("id").asString();
            }
        }
        throw new IllegalStateException("no viewer role");
    }
}
