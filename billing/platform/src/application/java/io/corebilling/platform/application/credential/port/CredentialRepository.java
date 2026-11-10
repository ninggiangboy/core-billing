package io.corebilling.platform.application.credential.port;

import io.corebilling.platform.api.tenant.TenantSystemId;
import io.corebilling.platform.domain.credential.ApiCredential;
import io.corebilling.platform.domain.credential.CredentialId;
import java.util.List;
import java.util.Optional;

public interface CredentialRepository {

    void add(ApiCredential credential);

    void update(ApiCredential credential);

    Optional<ApiCredential> findById(CredentialId id);

    /** Finds within the current tenant only; another tenant's key id is invisible. */
    Optional<ApiCredential> findByKeyId(String keyId);

    List<ApiCredential> findByTenantSystem(TenantSystemId tenantSystemId);
}
