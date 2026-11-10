package io.corebilling.http;

import static org.assertj.core.api.Assertions.assertThat;

import io.corebilling.Application;
import io.corebilling.platform.api.Permission;
import io.corebilling.platform.api.RequiresPermission;
import io.corebilling.platform.application.BootstrapPlatformAdminUseCase;
import io.corebilling.support.HttpWorld;
import io.corebilling.support.HttpWorld.Account;
import io.corebilling.support.KeycloakFixture;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.RealKeycloakConfig;
import io.corebilling.support.ScopeTestSupport;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
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
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

/** Real Keycloak tokens against the real filter chain: who gets in, on which host, and with which rights. */
@SpringBootTest(classes = Application.class)
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccessControlHttpIntegrationTest {

    /** Every endpoint, the permission it declares and where it lives. Kept in sync with the controllers by a test. */
    private record Endpoint(HttpMethod method, String path, Permission permission) {}

    private static final String ID = UUID.randomUUID().toString();
    private static final List<Endpoint> PLATFORM_ENDPOINTS = List.of(
            new Endpoint(HttpMethod.POST, "/api/v1/tenants", Permission.TENANT_CREATE),
            new Endpoint(HttpMethod.GET, "/api/v1/tenants", Permission.TENANT_READ),
            new Endpoint(HttpMethod.GET, "/api/v1/tenants/" + ID, Permission.TENANT_READ),
            new Endpoint(HttpMethod.POST, "/api/v1/tenants/" + ID + "/deactivate", Permission.TENANT_DEACTIVATE),
            new Endpoint(HttpMethod.GET, "/api/v1/tenants/" + ID + "/domains", Permission.TENANT_READ),
            new Endpoint(HttpMethod.POST, "/api/v1/tenants/" + ID + "/domains", Permission.TENANT_DOMAIN_MANAGE));
    private static final List<Endpoint> SHARED_ENDPOINTS = List.of(
            new Endpoint(HttpMethod.POST, "/api/v1/users", Permission.USER_MANAGE),
            new Endpoint(HttpMethod.GET, "/api/v1/users", Permission.USER_READ),
            new Endpoint(HttpMethod.GET, "/api/v1/users/" + ID, Permission.USER_READ),
            new Endpoint(HttpMethod.PUT, "/api/v1/users/" + ID + "/roles/" + ID, Permission.USER_MANAGE),
            new Endpoint(HttpMethod.DELETE, "/api/v1/users/" + ID + "/roles/" + ID, Permission.USER_MANAGE),
            new Endpoint(HttpMethod.POST, "/api/v1/roles", Permission.ROLE_MANAGE),
            new Endpoint(HttpMethod.GET, "/api/v1/roles", Permission.ROLE_READ),
            new Endpoint(HttpMethod.GET, "/api/v1/roles/" + ID, Permission.ROLE_READ),
            new Endpoint(HttpMethod.PUT, "/api/v1/roles/" + ID + "/permissions", Permission.ROLE_MANAGE),
            new Endpoint(HttpMethod.GET, "/api/v1/permissions", Permission.ROLE_READ));
    private static final List<Endpoint> TENANT_ENDPOINTS = List.of(
            new Endpoint(HttpMethod.POST, "/api/v1/tenant-systems", Permission.TENANT_SYSTEM_MANAGE),
            new Endpoint(HttpMethod.GET, "/api/v1/tenant-systems", Permission.TENANT_SYSTEM_READ),
            new Endpoint(HttpMethod.GET, "/api/v1/tenant-systems/" + ID, Permission.TENANT_SYSTEM_READ),
            new Endpoint(HttpMethod.POST, "/api/v1/tenant-systems/" + ID + "/credentials", Permission.CREDENTIAL_MANAGE),
            new Endpoint(HttpMethod.GET, "/api/v1/tenant-systems/" + ID + "/credentials", Permission.CREDENTIAL_MANAGE),
            new Endpoint(HttpMethod.POST, "/api/v1/tenant-systems/" + ID + "/credentials/rotate", Permission.CREDENTIAL_MANAGE),
            new Endpoint(HttpMethod.DELETE, "/api/v1/tenant-systems/" + ID + "/credentials/" + ID, Permission.CREDENTIAL_MANAGE));

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("requestMappingHandlerMapping") RequestMappingHandlerMapping handlerMapping;
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

    @Test
    void everyEndpointIsCoveredByThisTestAndDeclaresARule() {
        Set<String> documented = new TreeSet<>();
        List<Endpoint> all = new ArrayList<>(PLATFORM_ENDPOINTS);
        all.addAll(SHARED_ENDPOINTS);
        all.addAll(TENANT_ENDPOINTS);
        all.forEach(e -> documented.add(e.method() + " " + e.permission().code()));

        Set<String> actual = new TreeSet<>();
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            RequestMappingInfo info = entry.getKey();
            HandlerMethod method = entry.getValue();
            if (!method.getBeanType().getName().startsWith("io.corebilling.platform.adapter.web")) {
                continue;
            }
            RequiresPermission required = method.getMethodAnnotation(RequiresPermission.class);
            if (required == null) {
                assertThat(method.getMethod().getName()).as("only /me may skip a permission").isEqualTo("me");
                continue;
            }
            info.getMethodsCondition().getMethods().forEach(m -> actual.add(m + " " + required.value().code()));
        }
        assertThat(actual).as("the endpoint tables in this test must list every protected endpoint").containsExactlyElementsOf(documented);
    }

    @Test
    void everyControllerEndpointInEveryModuleDeclaresAnAccessRule() {
        int checked = 0;
        for (var entry : handlerMapping.getHandlerMethods().entrySet()) {
            HandlerMethod method = entry.getValue();
            if (!method.getBeanType().getName().matches("io\\.corebilling\\.[a-z]+\\.adapter\\.web\\..*")) {
                continue;
            }
            checked++;
            assertThat(method.hasMethodAnnotation(RequiresPermission.class) || method.hasMethodAnnotation(io.corebilling.platform.api.Authenticated.class))
                    .as("%s declares neither @RequiresPermission nor @Authenticated", method.getShortLogMessage()).isTrue();
        }
        assertThat(checked).as("controllers of platform, customer and money were all inspected").isGreaterThan(40);
    }

    @Test
    void requestsWithoutAValidTokenAreUnauthorised() {
        assertThat(world.status(HttpMethod.GET, acme.host(), null, "/api/v1/me", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, acme.host(), "garbage", "/api/v1/me", null)).isEqualTo(401);
        assertThat(world.status(HttpMethod.GET, acme.host(), acme.adminToken() + "x", "/api/v1/me", null)).isEqualTo(401);
    }

    @Test
    void aTokenWithoutTheBillingAudienceIsRejected() {
        // The admin client's token is validly signed by the same realm but is meant for Keycloak's own API.
        var form = new org.springframework.util.LinkedMultiValueMap<String, String>();
        form.add("grant_type", "client_credentials");
        form.add("client_id", KeycloakFixture.ADMIN_CLIENT_ID);
        form.add("client_secret", KeycloakFixture.adminClientSecret());
        String token = org.springframework.web.client.RestClient.create(KeycloakFixture.baseUrl()).post()
                .uri("/realms/{r}/protocol/openid-connect/token", KeycloakFixture.REALM)
                .contentType(org.springframework.http.MediaType.APPLICATION_FORM_URLENCODED).body(form).retrieve().body(
                        tools.jackson.databind.JsonNode.class).get("access_token").asString();
        assertThat(world.status(HttpMethod.GET, acme.host(), token, "/api/v1/me", null)).isEqualTo(401);
    }

    @Test
    void aTenantAdministratorIsKnownToItsOwnHostOnly() {
        var me = world.body(world.call(HttpMethod.GET, acme.host(), acme.adminToken(), "/api/v1/me", null));
        assertThat(me.get("type").asString()).isEqualTo("USER");
        assertThat(me.get("scope").asString()).isEqualTo("TENANT");
        assertThat(me.get("tenantId").asString()).isEqualTo(acme.tenantId());

        assertThat(world.status(HttpMethod.GET, globex.host(), acme.adminToken(), "/api/v1/me", null))
                .as("a session for tenant A fails on tenant B").isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, world.platformHost(), acme.adminToken(), "/api/v1/me", null))
                .as("a tenant session fails on the platform host").isEqualTo(403);
        assertThat(world.status(HttpMethod.GET, acme.host(), world.platformAdminToken(), "/api/v1/me", null))
                .as("a platform session fails on a tenant host").isEqualTo(403);
    }

    @Test
    void theSameEmailInTwoTenantsLogsInToEachIndependently() {
        String shared = "twin-" + UUID.randomUUID() + "@example.com";
        for (Account account : List.of(acme, globex)) {
            world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(), "/api/v1/users",
                    HttpWorld.Map2.of("email", shared, "displayName", "Twin", "temporaryPassword", "Temp-pass-1")));
        }
        for (Account account : List.of(acme, globex)) {
            var users = world.body(world.call(HttpMethod.GET, account.host(), account.adminToken(), "/api/v1/users", null));
            assertThat(users.toString()).contains(shared);
        }
        for (Account account : List.of(acme, globex)) {
            var users = world.body(world.call(HttpMethod.GET, account.host(), account.adminToken(), "/api/v1/users", null));
            long sameEmail = java.util.stream.StreamSupport.stream(users.spliterator(), false)
                    .filter(user -> user.get("email").asString().equals(shared)).count();
            assertThat(sameEmail).as("one user with that email in " + account.code()).isEqualTo(1);
        }
    }

    @Test
    void aUserWithNoPermissionsIsForbiddenOnEveryEndpoint() {
        String token = world.newUserToken(acme, Set.of());
        assertThat(world.status(HttpMethod.GET, acme.host(), token, "/api/v1/me", null)).isEqualTo(200);
        for (Endpoint endpoint : concat(SHARED_ENDPOINTS, TENANT_ENDPOINTS, PLATFORM_ENDPOINTS)) {
            assertThat(world.status(endpoint.method(), acme.host(), token, endpoint.path(), java.util.Map.of()))
                    .as(endpoint.method() + " " + endpoint.path()).isEqualTo(403);
        }
    }

    @Test
    void platformPermissionsDoNotExistInsideATenantEvenForItsAdministrator() {
        for (Endpoint endpoint : PLATFORM_ENDPOINTS) {
            assertThat(world.status(endpoint.method(), acme.host(), acme.adminToken(), endpoint.path(), java.util.Map.of()))
                    .as(endpoint.method() + " " + endpoint.path()).isEqualTo(403);
        }
    }

    @Test
    void eachTenantEndpointOpensForExactlyItsOwnPermission() {
        Set<Permission> needed = new TreeSet<>();
        concat(SHARED_ENDPOINTS, TENANT_ENDPOINTS).forEach(e -> needed.add(e.permission()));
        for (Permission permission : needed) {
            String roleId = world.createRole(acme, "Only " + permission.code() + " " + UUID.randomUUID(), Set.of(permission.code()));
            String token = world.newUserToken(acme, Set.of(roleId));
            for (Endpoint endpoint : concat(SHARED_ENDPOINTS, TENANT_ENDPOINTS)) {
                int status = world.status(endpoint.method(), acme.host(), token, endpoint.path(), java.util.Map.of());
                if (endpoint.permission() == permission) {
                    assertThat(status).as("allowed: %s %s with %s", endpoint.method(), endpoint.path(), permission.code())
                            .isNotIn(401, 403);
                } else {
                    assertThat(status).as("denied: %s %s with only %s", endpoint.method(), endpoint.path(), permission.code())
                            .isEqualTo(403);
                }
            }
        }
    }

    @Test
    void thePlatformAdministratorReachesEveryPlatformEndpointButNoTenantOnlyOne() {
        for (Endpoint endpoint : concat(PLATFORM_ENDPOINTS, SHARED_ENDPOINTS)) {
            assertThat(world.status(endpoint.method(), world.platformHost(), world.platformAdminToken(), endpoint.path(), java.util.Map.of()))
                    .as("platform admin: %s %s", endpoint.method(), endpoint.path()).isNotIn(401, 403);
        }
        for (Endpoint endpoint : TENANT_ENDPOINTS) {
            assertThat(world.status(endpoint.method(), world.platformHost(), world.platformAdminToken(), endpoint.path(), java.util.Map.of()))
                    .as("tenant-only: %s %s", endpoint.method(), endpoint.path()).isEqualTo(403);
        }
    }

    @Test
    void deactivatingATenantStopsItsHost() {
        Account doomed = world.newTenant();
        assertThat(world.status(HttpMethod.POST, world.platformHost(), world.platformAdminToken(),
                "/api/v1/tenants/" + doomed.tenantId() + "/deactivate", null)).isEqualTo(200);
        assertThat(world.status(HttpMethod.GET, doomed.host(), doomed.adminToken(), "/api/v1/me", null)).isEqualTo(404);
    }

    @SafeVarargs
    private static List<Endpoint> concat(List<Endpoint>... lists) {
        List<Endpoint> all = new ArrayList<>();
        for (List<Endpoint> list : lists) {
            all.addAll(list);
        }
        return all;
    }
}
