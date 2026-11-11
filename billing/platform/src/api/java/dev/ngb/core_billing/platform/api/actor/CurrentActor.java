package dev.ngb.core_billing.platform.api.actor;

import java.util.UUID;

/** The authenticated {@link Actor} of the current request. */
public interface CurrentActor {

    /** @throws IllegalStateException when the request is not authenticated */
    Actor get();

    /**
     * The Tenant System behind the request, or null when a Tenant User is acting directly. Reads of another
     * context's data are narrowed to the customers that system mapped.
     */
    default UUID caller() {
        var system = get().tenantSystemId();
        return system == null ? null : system.value();
    }
}
