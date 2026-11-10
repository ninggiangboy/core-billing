package io.corebilling.customer.api;

import io.corebilling.sharedkernel.CustomerId;
import java.util.Objects;

/** What other contexts may know about a Customer. */
public record CustomerSummary(CustomerId id, String displayName, boolean active) {

    public CustomerSummary {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(displayName, "displayName");
    }
}
