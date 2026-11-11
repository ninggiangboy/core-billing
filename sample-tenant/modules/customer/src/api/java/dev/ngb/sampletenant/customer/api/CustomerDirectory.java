package dev.ngb.sampletenant.customer.api;

import java.util.UUID;

/** The customer facts other modules need, without exposing the customer module. */
public interface CustomerDirectory {

    record CustomerSnapshot(UUID id, UUID userId, String billingCustomerId, boolean mapped) {}

    CustomerSnapshot byUserId(UUID userId);
}
