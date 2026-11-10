package io.corebilling.boot.jobs;

import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.application.TenantQueries;
import io.corebilling.platform.domain.Tenant;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Runs background work once for every active Tenant, each inside that Tenant's own scope, because row-level
 * security means a job can only see one tenant at a time. A failure in one Tenant is logged and does not stop
 * the others.
 */
@Component
public class TenantSweep {

    private static final Logger LOG = LoggerFactory.getLogger(TenantSweep.class);

    private final TenantContext tenantContext;
    private final TenantQueries tenants;

    TenantSweep(TenantContext tenantContext, TenantQueries tenants) {
        this.tenantContext = tenantContext;
        this.tenants = tenants;
    }

    public void forEachActiveTenant(Consumer<TenantId> work) {
        List<Tenant> active = tenantContext.callAs(RequestScope.platform(), tenants::list).stream().filter(Tenant::isActive).toList();
        for (Tenant tenant : active) {
            try {
                tenantContext.runAs(RequestScope.tenant(tenant.id()), () -> work.accept(tenant.id()));
            } catch (RuntimeException failure) {
                LOG.error("background job failed for tenant {}", tenant.code(), failure);
            }
        }
    }
}
