package dev.ngb.core_billing.session.application.portal.port;

import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.util.Set;

/** The Tenant's allowlist of return URLs a Billing Session may point back to. */
public interface ReturnUrlRepository {

    Set<String> allowed();

    void replace(TenantId tenantId, Set<String> urls);
}
