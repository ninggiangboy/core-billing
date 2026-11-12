package dev.ngb.core_billing.platform.application.tenant.command;

import dev.ngb.core.id.TenantId;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.application.tenant.exception.TenantNotFoundException;
import dev.ngb.core_billing.platform.application.tenant.port.TenantRepository;
import dev.ngb.core_billing.platform.domain.tenant.Tenant;

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
