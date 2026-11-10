package dev.ngb.core_billing.platform.application.credential.query;

import java.util.List;
import java.util.UUID;

/** Read-only projections of API credentials. */
public interface CredentialReadRepository {

    List<CredentialView> findByTenantSystem(UUID tenantSystemId);
}
