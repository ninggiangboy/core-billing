package io.corebilling.platform.application.credential.query;

import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.platform.api.tenant.TenantSystemId;
import io.corebilling.platform.application.tenantsystem.query.TenantSystemReadRepository;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;

/** Reads a tenant system's credentials for the admin GET handler. */
public class CredentialQueries {

    private final UnitOfWork unitOfWork;
    private final CredentialReadRepository credentials;
    private final TenantSystemReadRepository systems;

    public CredentialQueries(UnitOfWork unitOfWork, CredentialReadRepository credentials, TenantSystemReadRepository systems) {
        this.unitOfWork = unitOfWork;
        this.credentials = credentials;
        this.systems = systems;
    }

    public List<CredentialView> list(TenantSystemId tenantSystemId) {
        return unitOfWork.transactional(() -> {
            systems.findById(tenantSystemId.value()).orElseThrow(() -> new NotFoundException("tenant system not found"));
            return credentials.findByTenantSystem(tenantSystemId.value());
        });
    }
}
