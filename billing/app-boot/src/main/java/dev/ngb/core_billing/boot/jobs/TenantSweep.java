package dev.ngb.core_billing.boot.jobs;

import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.tenant.query.TenantQueries;
import dev.ngb.core_billing.platform.application.tenant.query.TenantView;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.TenantId;
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
        List<TenantView> active = tenantContext.callAs(RequestScope.platform(), tenants::list).stream().filter(TenantView::isActive).toList();
        for (TenantView tenant : active) {
            try {
                tenantContext.runAs(RequestScope.tenant(new TenantId(tenant.id())), () -> work.accept(new TenantId(tenant.id())));
            } catch (RuntimeException failure) {
                LOG.error("background job failed for tenant {}", tenant.code(), failure);
            }
        }
    }
}
