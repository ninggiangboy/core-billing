package dev.ngb.core_billing.usage.api.lookup;

import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Read-only access to recorded usage for the contexts that price it (rating) or bill it (invoices). It answers
 * within the current tenant scope. Periods are half-open: {@code from} is included, {@code to} is not.
 */
public interface UsageLookup {

    /** Events of the given metrics, oldest first, at most {@code limit}. */
    List<UsageRecord> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit);

    /** Quantity and event count per metric. An empty {@code metrics} set means every metric. */
    List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to);
}
