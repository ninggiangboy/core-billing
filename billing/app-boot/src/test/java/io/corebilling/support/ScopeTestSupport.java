package io.corebilling.support;

import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.application.CreateTenantUseCase;
import io.corebilling.platform.domain.Tenant;
import io.corebilling.sharedkernel.TenantId;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.stereotype.Component;

/** Test helpers for running work in a scope and creating throwaway tenants. */
@Component
public class ScopeTestSupport {

    private final TenantContext tenantContext;
    private final CreateTenantUseCase createTenant;

    ScopeTestSupport(TenantContext tenantContext, CreateTenantUseCase createTenant) {
        this.tenantContext = tenantContext;
        this.createTenant = createTenant;
    }

    public <T> T inTenant(TenantId tenantId, Supplier<T> work) {
        return tenantContext.callAs(RequestScope.tenant(tenantId), work);
    }

    public void runInTenant(TenantId tenantId, Runnable work) {
        tenantContext.runAs(RequestScope.tenant(tenantId), work);
    }

    public void runOnPlatform(Runnable work) {
        tenantContext.runAs(RequestScope.platform(), work);
    }

    public <T> T onPlatform(Supplier<T> work) {
        return tenantContext.callAs(RequestScope.platform(), work);
    }

    public Tenant newTenant() {
        String code = "t" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return onPlatform(() -> createTenant.execute(new CreateTenantUseCase.Command(code, "Tenant " + code, "tester", "req-1")));
    }
}
