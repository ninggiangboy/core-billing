package io.corebilling.platform.application.tenant.query;

import io.corebilling.platform.application.tenant.TenantNotFoundException;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Read side of tenant administration (Platform scope). The reserved platform tenant is never listed. */
public class TenantQueries {

    private static final String PLATFORM_CODE = "platform";

    private final UnitOfWork unitOfWork;
    private final TenantReadRepository tenants;

    public TenantQueries(UnitOfWork unitOfWork, TenantReadRepository tenants) {
        this.unitOfWork = unitOfWork;
        this.tenants = tenants;
    }

    public TenantView get(UUID id) {
        return unitOfWork.transactional(() -> tenants.findById(id)
                .filter(tenant -> !PLATFORM_CODE.equals(tenant.code()))
                .orElseThrow(() -> new TenantNotFoundException(id.toString())));
    }

    public List<TenantView> list() {
        return unitOfWork.transactional(tenants::list);
    }

    public List<TenantDomainView> domains(UUID id) {
        return unitOfWork.transactional(() -> {
            get(id);
            return tenants.domains(id);
        });
    }
}
