package io.corebilling.customer.application.query;

import io.corebilling.customer.domain.customer.CustomerStatus;
import io.corebilling.sharedkernel.CustomerId;
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
