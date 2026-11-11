package dev.ngb.core_billing.platform.application.tenantsystem.command;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.application.tenantsystem.port.TenantSystemRepository;

/** Tenant Systems (the Tenant's integrated applications) of the current Tenant. */
public class CreateTenantSystemUseCase {

    private final UnitOfWork unitOfWork;
    private final TenantSystemRepository systems;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public CreateTenantSystemUseCase(
            UnitOfWork unitOfWork, TenantSystemRepository systems, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.systems = systems;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public TenantSystem create(Audit caller, String name) {
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            TenantSystem system = TenantSystem.create(new TenantSystemId(ids.newId()), tenantId, name, clock.now());
            systems.add(system);
            auditLog.record(AuditEntry.tenant(tenantId, caller.actor(), "tenant-system.created", "tenant-system/" + system.id(), caller.requestId(), clock.now()));
            return system;
        });
    }

}
