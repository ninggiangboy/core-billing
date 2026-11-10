package dev.ngb.core_billing.platform.application.tenant.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of tenants and their domains. */
public interface TenantReadRepository {

    List<TenantView> list();

    Optional<TenantView> findById(UUID id);

    List<TenantDomainView> domains(UUID tenantId);
}
