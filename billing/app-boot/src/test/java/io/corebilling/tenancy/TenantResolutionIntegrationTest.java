package io.corebilling.tenancy;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import io.corebilling.Application;
import io.corebilling.platform.api.actor.RequestScope;
import io.corebilling.platform.api.tenant.TenantContext;
import io.corebilling.platform.application.tenant.command.AddTenantDomainUseCase;
import io.corebilling.platform.application.tenant.command.DeactivateTenantUseCase;
import io.corebilling.platform.domain.tenant.Tenant;
import io.corebilling.support.PostgresContainerConfig;
import io.corebilling.support.ScopeTestSupport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import io.corebilling.platform.api.actor.Authenticated;

@SpringBootTest(classes = {Application.class, TenantResolutionIntegrationTest.ScopeEcho.class, TenantResolutionIntegrationTest.OpenTestPaths.class})
@AutoConfigureMockMvc
@Import(PostgresContainerConfig.class)
class TenantResolutionIntegrationTest {

    /** Reports the scope the filter established, as seen from inside a request. */
    @RestController
    static class ScopeEcho {
        private final TenantContext tenantContext;

        ScopeEcho(TenantContext tenantContext) {
            this.tenantContext = tenantContext;
        }

        @GetMapping("/test/scope")
        @io.corebilling.platform.api.actor.Authenticated
        String scope() {
            return switch (tenantContext.current()) {
                case RequestScope.Platform platform -> "platform";
                case RequestScope.Tenant tenant -> "tenant:" + tenant.tenantId();
            };
        }
    }

    /** Opens only the echo path, so these tests exercise hostname resolution without authentication. */
    @org.springframework.context.annotation.Configuration
    static class OpenTestPaths {
        @org.springframework.context.annotation.Bean
        @org.springframework.core.annotation.Order(0)
        org.springframework.security.web.SecurityFilterChain openEcho(org.springframework.security.config.annotation.web.builders.HttpSecurity http) {
            http.securityMatcher("/test/**").authorizeHttpRequests(a -> a.anyRequest().permitAll()).csrf(c -> c.disable());
            return http.build();
        }
    }

    @Autowired MockMvc mvc;
    @Autowired ScopeTestSupport scopes;
    @Autowired DeactivateTenantUseCase deactivate;
    @Autowired AddTenantDomainUseCase addDomain;
    @Value("${billing.tenancy.base-domain}") String baseDomain;

    @Test
    void platformHostnameResolvesToThePlatformScope() throws Exception {
        mvc.perform(get("/test/scope").header("Host", "platform." + baseDomain))
                .andExpect(status().isOk())
                .andExpect(content().string("platform"));
    }

    @Test
    void tenantSubdomainResolvesToThatTenant() throws Exception {
        Tenant tenant = scopes.newTenant();
        mvc.perform(get("/test/scope").header("Host", tenant.code() + "." + baseDomain + ":8080"))
                .andExpect(status().isOk())
                .andExpect(content().string("tenant:" + tenant.id()));
    }

    @Test
    void aClientSuppliedTenantIdNeverChangesTheScope() throws Exception {
        Tenant mine = scopes.newTenant();
        Tenant other = scopes.newTenant();
        mvc.perform(get("/test/scope")
                        .header("Host", mine.code() + "." + baseDomain)
                        .header("X-Tenant-Id", other.id().toString())
                        .param("tenant_id", other.id().toString()))
                .andExpect(content().string("tenant:" + mine.id()));
    }

    @Test
    void unknownAndMalformedHostsGetOneGenericAnswer() throws Exception {
        for (String host : new String[] {"nobody." + baseDomain, "evil.example.com", "bad_host!", baseDomain}) {
            mvc.perform(get("/test/scope").header("Host", host))
                    .andExpect(status().isNotFound())
                    .andExpect(content().json("{\"error\":\"not_found\"}"));
        }
    }

    @Test
    void aDeactivatedTenantStopsResolving() throws Exception {
        Tenant tenant = scopes.newTenant();
        scopes.onPlatform(() -> deactivate.execute(new DeactivateTenantUseCase.Command(tenant.id(), "tester", "req")));
        mvc.perform(get("/test/scope").header("Host", tenant.code() + "." + baseDomain)).andExpect(status().isNotFound());
    }

    @Test
    void anAdditionalDomainResolvesToTheSameTenant() throws Exception {
        Tenant tenant = scopes.newTenant();
        String extra = "alias-" + tenant.code() + "." + baseDomain;
        scopes.onPlatform(() -> addDomain.execute(new AddTenantDomainUseCase.Command(tenant.id(), extra, "tester", "req")));
        mvc.perform(get("/test/scope").header("Host", extra)).andExpect(content().string("tenant:" + tenant.id()));
        assertThat(extra).isNotBlank();
    }
}
