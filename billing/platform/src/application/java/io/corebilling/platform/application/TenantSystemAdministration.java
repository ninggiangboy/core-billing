package io.corebilling.platform.application;

import io.corebilling.platform.api.NotFoundException;
import io.corebilling.platform.api.AuditEntry;
import io.corebilling.platform.api.AuditLog;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.platform.domain.TenantSystem;
import io.corebilling.sharedkernel.Clock;
import io.corebilling.sharedkernel.IdGenerator;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;

/** Tenant Systems (the Tenant's integrated applications) of the current Tenant. */
public class TenantSystemAdministration {

    private final UnitOfWork unitOfWork;
    private final TenantSystemRepository systems;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public TenantSystemAdministration(
            UnitOfWork unitOfWork, TenantSystemRepository systems, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.systems = systems;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public TenantSystem create(Caller caller, String name) {
        return unitOfWork.transactional(() -> {
            TenantId tenantId = tenantContext.requireTenant();
            TenantSystem system = TenantSystem.create(new TenantSystemId(ids.newId()), tenantId, name, clock.now());
            systems.add(system);
            auditLog.record(AuditEntry.tenant(tenantId, caller.actor(), "tenant-system.created", "tenant-system/" + system.id(), caller.requestId(), clock.now()));
            return system;
        });
    }

    public TenantSystem get(TenantSystemId id) {
        return unitOfWork.transactional(() -> systems.findById(id).orElseThrow(() -> new NotFoundException("tenant system not found")));
    }

    public List<TenantSystem> list() {
        return unitOfWork.transactional(systems::list);
    }
}
