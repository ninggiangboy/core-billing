package dev.ngb.core_billing.platform.api.tenant;

import dev.ngb.core_billing.sharedkernel.id.TenantId;
import java.util.function.Supplier;
import dev.ngb.core_billing.platform.api.actor.RequestScope;

/** Access to the current {@link RequestScope}; the implementation binds it for the duration of a call. */
public interface TenantContext {

    /** @throws IllegalStateException when no scope is bound (the caller forgot to establish one) */
    RequestScope current();

    /** Runs {@code work} with {@code scope} bound, restoring the previous state afterwards. */
    <T> T callAs(RequestScope scope, Supplier<T> work);

    default void runAs(RequestScope scope, Runnable work) {
        callAs(scope, () -> {
            work.run();
            return null;
        });
    }

    /** @throws IllegalStateException when the current scope is the Platform */
    default TenantId requireTenant() {
        if (current() instanceof RequestScope.Tenant tenant) {
            return tenant.tenantId();
        }
        throw new IllegalStateException("a tenant scope is required");
    }
}
