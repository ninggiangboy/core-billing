package dev.ngb.core_billing.customer.api.lookup;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import java.util.Optional;

/**
 * Read-only lookup for the contexts that act on a Customer (money, payment). It answers within the current
 * tenant scope, so another tenant's customer is simply absent.
 */
public interface CustomerLookup {

    Optional<CustomerSummary> find(CustomerId id);
}
