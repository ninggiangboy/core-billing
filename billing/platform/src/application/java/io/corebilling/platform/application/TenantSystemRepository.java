package io.corebilling.platform.application;

import io.corebilling.platform.api.ConflictException;
import io.corebilling.platform.api.TenantSystemId;
import io.corebilling.platform.domain.TenantSystem;
import java.util.List;
import java.util.Optional;

public interface TenantSystemRepository {

    /** @throws ConflictException when the name is already used in the tenant */
    void add(TenantSystem system);

    void update(TenantSystem system);

    Optional<TenantSystem> findById(TenantSystemId id);

    List<TenantSystem> list();
}
