package io.corebilling.platform.application.tenantsystem.query;

import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Reads tenant systems for the admin GET handlers. */
public class TenantSystemQueries {

    private final UnitOfWork unitOfWork;
    private final TenantSystemReadRepository systems;

    public TenantSystemQueries(UnitOfWork unitOfWork, TenantSystemReadRepository systems) {
        this.unitOfWork = unitOfWork;
        this.systems = systems;
    }

    public List<TenantSystemView> list() {
        return unitOfWork.transactional(systems::list);
    }

    public TenantSystemView get(UUID id) {
        return unitOfWork.transactional(() -> systems.findById(id).orElseThrow(() -> new NotFoundException("tenant system not found")));
    }
}
