package io.corebilling.boot.config;

import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.application.EnsurePlatformDomainUseCase;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/** Maps platform.&lt;base-domain&gt; to Platform Administration on every start. */
@Component
class PlatformBootstrap implements ApplicationRunner {

    private final EnsurePlatformDomainUseCase ensurePlatformDomain;
    private final TenantContext tenantContext;
    private final String baseDomain;

    PlatformBootstrap(
            EnsurePlatformDomainUseCase ensurePlatformDomain,
            TenantContext tenantContext,
            @Value("${billing.tenancy.base-domain}") String baseDomain) {
        this.ensurePlatformDomain = ensurePlatformDomain;
        this.tenantContext = tenantContext;
        this.baseDomain = baseDomain;
    }

    @Override
    public void run(ApplicationArguments args) {
        tenantContext.runAs(RequestScope.platform(), () -> ensurePlatformDomain.execute("platform." + baseDomain));
    }
}
