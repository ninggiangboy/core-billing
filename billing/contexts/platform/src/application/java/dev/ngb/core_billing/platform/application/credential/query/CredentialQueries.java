package dev.ngb.core_billing.platform.application.credential.query;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.application.tenantsystem.query.TenantSystemReadRepository;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
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
