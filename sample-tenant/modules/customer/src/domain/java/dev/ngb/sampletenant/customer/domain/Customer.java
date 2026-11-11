package dev.ngb.sampletenant.customer.domain;

import dev.ngb.sampletenant.shared.id.Ids;
import java.time.Instant;
import java.util.UUID;

/**
 * A local user's Billing Customer. The local account is the Tenant System's own identity; the
 * Billing id is stored as the mapping and is absent until Billing has answered.
 */
public record Customer(
        UUID id,
        UUID userId,
        String billingCustomerId,
        String displayName,
        String email,
        CustomerStatus status,
        Instant createdAt) {

    public static Customer pending(UUID userId, String displayName, String email, Instant now) {
        return new Customer(Ids.newId(), userId, null, displayName, email, CustomerStatus.PENDING, now);
    }

    public Customer mapped(String nextBillingCustomerId) {
        return new Customer(id, userId, nextBillingCustomerId, displayName, email, CustomerStatus.MAPPED, createdAt);
    }

    public Customer disabled() {
        return new Customer(id, userId, billingCustomerId, displayName, email, CustomerStatus.DISABLED, createdAt);
    }

    public boolean mapped() {
        return status == CustomerStatus.MAPPED && billingCustomerId != null;
    }
}
