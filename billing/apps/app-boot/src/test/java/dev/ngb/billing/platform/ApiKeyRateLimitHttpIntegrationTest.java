package dev.ngb.billing.platform;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.billing.Application;
import dev.ngb.billing.platform.application.user.command.BootstrapPlatformAdminUseCase;
import dev.ngb.billing.support.HttpWorld;
import dev.ngb.billing.support.HttpWorld.Account;
import dev.ngb.billing.support.PostgresContainerConfig;
import dev.ngb.billing.support.RealKeycloakConfig;
import dev.ngb.billing.support.ScopeTestSupport;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpMethod;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(
        classes = Application.class,
        properties = {"billing.security.api-key-rate-limit.burst=3", "billing.security.api-key-rate-limit.per-second=0"})
@AutoConfigureMockMvc
@Import({PostgresContainerConfig.class, RealKeycloakConfig.class})
class ApiKeyRateLimitHttpIntegrationTest {

    @Autowired MockMvc mvc;
    @Autowired BootstrapPlatformAdminUseCase bootstrap;
    @Autowired ScopeTestSupport scopes;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    @Test
    void aKeyThatExceedsItsLimitIsToldToSlowDown() {
        HttpWorld world = new HttpWorld(mvc, bootstrap, scopes, baseDomain);
        Account account = world.newTenant();
        String systemId = world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(), "/api/v1/tenant-systems",
                HttpWorld.Map2.of("name", "Limited"))).get("id").asString();
        String key = world.body(world.call(HttpMethod.POST, account.host(), account.adminToken(),
                "/api/v1/tenant-systems/" + systemId + "/credentials", HttpWorld.Map2.of("label", "x", "scopes", List.of()))).get("key").asString();

        for (int i = 0; i < 3; i++) {
            assertThat(world.status(HttpMethod.GET, account.host(), key, "/api/v1/me", null)).isEqualTo(200);
        }
        var limited = world.call(HttpMethod.GET, account.host(), key, "/api/v1/me", null);

        assertThat(limited.getResponse().getStatus()).isEqualTo(429);
        assertThat(limited.getResponse().getHeader("Retry-After")).isEqualTo("1");
        assertThat(world.status(HttpMethod.GET, account.host(), account.adminToken(), "/api/v1/me", null)).as("users are not limited by a key").isEqualTo(200);
    }
}
