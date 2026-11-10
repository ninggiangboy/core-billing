package io.corebilling.platform.application.tenantsystem.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of tenant systems. */
public interface TenantSystemReadRepository {

    List<TenantSystemView> list();

    Optional<TenantSystemView> findById(UUID id);
}
