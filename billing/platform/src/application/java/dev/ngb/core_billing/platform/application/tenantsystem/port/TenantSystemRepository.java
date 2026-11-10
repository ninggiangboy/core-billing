package dev.ngb.core_billing.platform.application.tenantsystem.port;

import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.tenant.TenantSystemId;
import dev.ngb.core_billing.platform.domain.tenantsystem.TenantSystem;
import java.util.Optional;

public interface TenantSystemRepository {

    /** @throws ConflictException when the name is already used in the tenant */
    void add(TenantSystem system);

    void update(TenantSystem system);

    Optional<TenantSystem> findById(TenantSystemId id);
}
