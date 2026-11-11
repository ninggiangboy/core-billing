package dev.ngb.core_billing.platform.application.tenant.port;

import dev.ngb.core_billing.platform.domain.tenant.Tenant;
import dev.ngb.core_billing.platform.domain.tenant.TenantCode;
import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.util.Optional;

public interface TenantRepository {

    /** @throws TenantCodeAlreadyUsedException when the unique code constraint rejects the insert */
    void add(Tenant tenant);

    void update(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findByCode(TenantCode code);
}
