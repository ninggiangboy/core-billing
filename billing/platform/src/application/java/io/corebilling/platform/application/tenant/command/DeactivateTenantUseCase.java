package io.corebilling.platform.application.tenant.command;

import io.corebilling.platform.api.audit.AuditEntry;
import io.corebilling.platform.api.audit.AuditLog;
import io.corebilling.platform.api.actor.RequestScope;
import io.corebilling.platform.domain.tenant.Tenant;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.platform.application.tenant.port.TenantRepository;
import io.corebilling.platform.application.tenant.TenantNotFoundException;

/** Deactivates a Tenant; its hostnames stop resolving. Runs at Platform scope. */
public class DeactivateTenantUseCase {

    public record Command(TenantId tenantId, String actor, String requestId) {}

    private final UnitOfWork unitOfWork;
    private final TenantRepository tenants;
    private final AuditLog auditLog;
    private final Clock clock;

    public DeactivateTenantUseCase(
            UnitOfWork unitOfWork, TenantRepository tenants, AuditLog auditLog, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.tenants = tenants;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public Tenant execute(Command command) {
        return unitOfWork.transactional(() -> {
            if (RequestScope.PLATFORM_TENANT_ID.equals(command.tenantId())) {
                throw new IllegalArgumentException("the platform tenant cannot be deactivated");
            }
            Tenant tenant = tenants.findById(command.tenantId())
                    .orElseThrow(() -> new TenantNotFoundException(command.tenantId().toString()));
            tenant.deactivate();
            tenants.update(tenant);
            auditLog.record(AuditEntry.platform(
                    command.actor(),
                    "tenant.deactivated",
                    "tenant/" + tenant.id(),
                    command.requestId(),
                    clock.now()));
            return tenant;
        });
    }
}
