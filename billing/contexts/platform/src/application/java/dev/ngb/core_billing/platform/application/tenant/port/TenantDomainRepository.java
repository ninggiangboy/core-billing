package dev.ngb.core_billing.platform.application.tenant.port;

import dev.ngb.core_billing.platform.domain.tenant.Hostname;
import dev.ngb.core_billing.platform.domain.tenant.TenantDomain;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.util.List;
import java.util.Optional;

public interface TenantDomainRepository {

    /** @throws HostnameAlreadyMappedException when the unique hostname constraint rejects the insert */
    void add(TenantDomain domain);

    Optional<TenantDomain> findByHostname(Hostname hostname);

    List<TenantDomain> findByTenant(TenantId tenantId);
}
