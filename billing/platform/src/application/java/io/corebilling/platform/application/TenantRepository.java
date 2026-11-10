package io.corebilling.platform.application;

import io.corebilling.platform.domain.Tenant;
import io.corebilling.platform.domain.TenantCode;
import io.corebilling.sharedkernel.TenantId;
import java.util.List;
import java.util.Optional;

public interface TenantRepository {

    /** @throws TenantCodeAlreadyUsedException when the unique code constraint rejects the insert */
    void add(Tenant tenant);

    void update(Tenant tenant);

    Optional<Tenant> findById(TenantId id);

    Optional<Tenant> findByCode(TenantCode code);

    List<Tenant> list();
}
