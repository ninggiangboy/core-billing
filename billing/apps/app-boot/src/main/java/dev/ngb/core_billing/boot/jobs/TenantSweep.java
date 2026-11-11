package dev.ngb.core_billing.boot.jobs;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.application.tenant.query.TenantQueries;
import dev.ngb.core_billing.platform.application.tenant.query.TenantView;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Semaphore;
import java.util.function.Consumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Runs background work once for every active Tenant, each inside that Tenant's own scope, because row-level
 * security means a job can only see one tenant at a time. A failure in one Tenant is logged and does not stop
 * the others.
 *
 * <p>With {@code billing.jobs.tenant-parallelism} above one, tenants run on virtual threads bounded by one
 * semaphore shared by every job, so a tenant whose consumer is slow cannot delay the tenants behind it and the
 * whole worker never asks the database for more connections than its pool. The scope is bound inside each task:
 * {@link dev.ngb.core_billing.platform.api.actor.RequestScope} lives in a {@code ScopedValue}, which does not
 * cross a thread boundary.
 */
@Profile("!api & !migrate")
@Component
public class TenantSweep {

    private static final Logger LOG = LoggerFactory.getLogger(TenantSweep.class);

    private final TenantContext tenantContext;
    private final TenantQueries tenants;
    private final int parallelism;
    private final Semaphore permits;

    TenantSweep(TenantContext tenantContext, TenantQueries tenants, @Value("${billing.jobs.tenant-parallelism:1}") int parallelism) {
        this.tenantContext = tenantContext;
        this.tenants = tenants;
        this.parallelism = parallelism;
        this.permits = new Semaphore(Math.max(1, parallelism));
    }

    public void forEachActiveTenant(Consumer<TenantId> work) {
        List<TenantView> active = tenantContext.callAs(RequestScope.platform(), tenants::list).stream().filter(TenantView::isActive).toList();
        if (parallelism <= 1) {
            for (TenantView tenant : active) {
                TenantId tenantId = new TenantId(tenant.id());
                try {
                    tenantContext.runAs(RequestScope.tenant(tenantId), () -> work.accept(tenantId));
                } catch (RuntimeException failure) {
                    LOG.error("background job failed for tenant {}", tenant.code(), failure);
                }
            }
            return;
        }
        try (ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor()) {
            List<Callable<Void>> tasks = active.stream()
                    .<Callable<Void>>map(tenant -> () -> {
                        runBounded(new TenantId(tenant.id()), tenant.code(), work);
                        return null;
                    })
                    .toList();
            executor.invokeAll(tasks);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        }
    }

    private void runBounded(TenantId tenantId, String code, Consumer<TenantId> work) {
        boolean acquired = false;
        try {
            permits.acquire();
            acquired = true;
            tenantContext.runAs(RequestScope.tenant(tenantId), () -> work.accept(tenantId));
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
        } catch (RuntimeException failure) {
            LOG.error("background job failed for tenant {}", code, failure);
        } finally {
            if (acquired) {
                permits.release();
            }
        }
    }
}
