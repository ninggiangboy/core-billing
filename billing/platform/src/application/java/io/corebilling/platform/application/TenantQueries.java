package io.corebilling.platform.application;

import io.corebilling.platform.domain.Tenant;
import io.corebilling.platform.domain.TenantCode;
import io.corebilling.platform.domain.TenantDomain;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;

/** Read side of tenant administration (Platform scope). The reserved platform tenant is never listed. */
public class TenantQueries {

    private final UnitOfWork unitOfWork;
    private final TenantRepository tenants;
    private final TenantDomainRepository domains;

    public TenantQueries(UnitOfWork unitOfWork, TenantRepository tenants, TenantDomainRepository domains) {
        this.unitOfWork = unitOfWork;
        this.tenants = tenants;
        this.domains = domains;
    }

    public Tenant get(TenantId id) {
        return unitOfWork.transactional(() -> tenants.findById(id)
                .filter(tenant -> !TenantCode.PLATFORM.equals(tenant.code().value()))
                .orElseThrow(() -> new TenantNotFoundException(id.toString())));
    }

    public List<Tenant> list() {
        return unitOfWork.transactional(() -> tenants.list().stream()
                .filter(tenant -> !TenantCode.PLATFORM.equals(tenant.code().value()))
                .toList());
    }

    public List<TenantDomain> domains(TenantId id) {
        return unitOfWork.transactional(() -> {
            get(id);
            return domains.findByTenant(id);
        });
    }
}
