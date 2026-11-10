package io.corebilling.platform.application.tenant.port;

import io.corebilling.platform.domain.tenant.Hostname;
import io.corebilling.platform.domain.tenant.TenantDomain;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;

public interface TenantDomainRepository {

    /** @throws HostnameAlreadyMappedException when the unique hostname constraint rejects the insert */
    void add(TenantDomain domain);

    Optional<TenantDomain> findByHostname(Hostname hostname);

    List<TenantDomain> findByTenant(TenantId tenantId);
}
