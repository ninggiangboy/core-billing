package io.corebilling.platform.application.tenantsystem.port;

import io.corebilling.platform.api.error.ConflictException;
import io.corebilling.platform.api.tenant.TenantSystemId;
import io.corebilling.platform.domain.tenantsystem.TenantSystem;
import java.util.Optional;

public interface TenantSystemRepository {

    /** @throws ConflictException when the name is already used in the tenant */
    void add(TenantSystem system);

    void update(TenantSystem system);

    Optional<TenantSystem> findById(TenantSystemId id);
}
