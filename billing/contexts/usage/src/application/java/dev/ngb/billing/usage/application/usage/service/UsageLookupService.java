package dev.ngb.billing.usage.application.usage.service;

import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.usage.api.lookup.UsageLookup;
import dev.ngb.billing.usage.api.lookup.UsageRecord;
import dev.ngb.billing.usage.api.lookup.UsageTotal;
import dev.ngb.billing.usage.application.usage.query.UsageQueries;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** The {@link UsageLookup} other contexts read usage through: the same reads as the API, outside any caller scope. */
public class UsageLookupService implements UsageLookup {

    private final UsageQueries queries;

    public UsageLookupService(UsageQueries queries) {
        this.queries = queries;
    }

    @Override
    public List<UsageRecord> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit) {
        return queries.find(customerId, metrics, from, to, limit);
    }

    @Override
    public List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to) {
        return queries.totals(customerId, metrics, from, to, null);
    }
}
