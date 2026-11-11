package dev.ngb.core_billing.boot.infrastructure.tenancy;

import dev.ngb.core_billing.platform.api.actor.RequestScope;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import java.util.function.Supplier;

/**
 * Carries the {@link RequestScope} in a {@link ScopedValue}: bound for exactly the duration of a call,
 * invisible to other threads, and restored automatically, so a scope can never leak to another request.
 */
public class ScopedTenantContext implements TenantContext {

    private static final ScopedValue<RequestScope> SCOPE = ScopedValue.newInstance();

    @Override
    public RequestScope current() {
        if (!SCOPE.isBound()) {
            throw new IllegalStateException("no request scope is bound; resolve one before touching the database");
        }
        return SCOPE.get();
    }

    @Override
    public <T> T callAs(RequestScope scope, Supplier<T> work) {
        return ScopedValue.where(SCOPE, scope).call(work::get);
    }
}
