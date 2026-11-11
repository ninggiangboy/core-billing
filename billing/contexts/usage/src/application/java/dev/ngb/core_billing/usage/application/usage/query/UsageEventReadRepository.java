package dev.ngb.core_billing.usage.application.usage.query;

import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.usage.api.lookup.UsageEventId;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Read-only projections over usage events. No write side and no aggregate is involved. */
public interface UsageEventReadRepository {

    /** Newest first; {@code before} is the id of the last row of the previous page. */
    List<UsageEventView> page(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit);

    List<UsageRecordView> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit);

    List<UsageTotalView> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to);
}
