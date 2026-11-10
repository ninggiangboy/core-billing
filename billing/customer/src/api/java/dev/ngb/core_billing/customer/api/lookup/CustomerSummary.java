package dev.ngb.core_billing.customer.api.lookup;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.util.Objects;

/** What other contexts may know about a Customer. */
public record CustomerSummary(CustomerId id, String displayName, boolean active) {

    public CustomerSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
