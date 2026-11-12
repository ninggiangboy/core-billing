package dev.ngb.sampletenant.customer.application.customer.query;

import dev.ngb.sampletenant.customer.domain.customer.CustomerStatus;
import java.time.Instant;
import java.util.UUID;

/** A local customer as the tenant's screens render it. */
public record CustomerView(
        UUID id,
        UUID userId,
        String billingCustomerId,
        String displayName,
        String email,
        CustomerStatus status,
        Instant createdAt) {

    public boolean mapped() {
        return status == CustomerStatus.MAPPED && billingCustomerId != null;
    }
}
