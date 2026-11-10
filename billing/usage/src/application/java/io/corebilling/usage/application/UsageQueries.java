package io.corebilling.usage.application;

import io.corebilling.customer.api.lookup.CustomerLookup;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.usage.api.UsageEventId;
import io.corebilling.usage.api.UsageLookup;
import io.corebilling.usage.api.UsageRecord;
import io.corebilling.usage.api.UsageTotal;
import io.corebilling.usage.domain.UsageEvent;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Reads usage of the current tenant: history for the API, and the {@link UsageLookup} other contexts use. */
public class UsageQueries implements UsageLookup {

    public static final int MAX_PAGE = 200;

    private final UnitOfWork unitOfWork;
    private final UsageEventRepository events;
    private final CustomerLookup customers;

    public UsageQueries(UnitOfWork unitOfWork, UsageEventRepository events, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.customers = customers;
    }

    /** Newest first; {@code metric}, {@code from}, {@code to} and {@code before} are optional filters. */
    public List<UsageEvent> history(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        requireWindow(from, to);
        return unitOfWork.transactional(() -> {
            requireCustomer(customerId);
            return events.page(customerId, metric, from, to, before, Math.min(limit, MAX_PAGE));
        });
    }

    @Override
    public List<UsageRecord> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit) {
        requireWindow(from, to);
        if (from == null || to == null) {
            throw new IllegalArgumentException("a period needs both from and to");
        }
        return unitOfWork.transactional(() -> events.find(customerId, metrics, from, to, limit));
    }

    @Override
    public List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to) {
        requireWindow(from, to);
        if (from == null || to == null) {
            throw new IllegalArgumentException("a period needs both from and to");
        }
        return unitOfWork.transactional(() -> {
            requireCustomer(customerId);
            return events.totals(customerId, metrics, from, to);
        });
    }

    private void requireCustomer(CustomerId customerId) {
        customers.find(customerId).orElseThrow(() -> new NotFoundException("customer not found"));
    }

    private static void requireWindow(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
