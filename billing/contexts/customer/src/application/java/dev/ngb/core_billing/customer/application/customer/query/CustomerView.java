package dev.ngb.core_billing.customer.application.customer.query;

import dev.ngb.core_billing.customer.domain.customer.CustomerStatus;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.time.Instant;
import java.util.List;

/** A customer as the admin GET handlers show it. */
public record CustomerView(
        CustomerId id,
        String displayName,
        String email,
        CustomerStatus status,
        Instant createdAt,
        List<CustomerIdentityView> identities) {

    public boolean isActive() {
        return status == CustomerStatus.ACTIVE;
    }
}
