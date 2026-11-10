package io.corebilling.platform.application;

import io.corebilling.platform.domain.Hostname;
import io.corebilling.platform.domain.TenantDomain;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;

public interface TenantDomainRepository {

    /** @throws HostnameAlreadyMappedException when the unique hostname constraint rejects the insert */
    void add(TenantDomain domain);

    Optional<TenantDomain> findByHostname(Hostname hostname);

    List<TenantDomain> findByTenant(TenantId tenantId);
}
