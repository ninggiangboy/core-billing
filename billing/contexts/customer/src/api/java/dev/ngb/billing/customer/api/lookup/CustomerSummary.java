package dev.ngb.billing.customer.api.lookup;

import dev.ngb.billing.sharedkernel.id.CustomerId;
import java.util.Objects;

/** What other contexts may know about a Customer. */
public record CustomerSummary(CustomerId id, String displayName, boolean active) {

    public CustomerSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
