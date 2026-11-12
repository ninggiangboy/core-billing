package dev.ngb.core_billing.usage.application.usage.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core.util.PageSize;
import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.usage.api.lookup.UsageEventId;
import dev.ngb.core_billing.usage.api.lookup.UsageRecord;
import dev.ngb.core_billing.usage.api.lookup.UsageTotal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Reads usage of the current tenant: history and totals for the API, and the period reads behind the usage lookup. */
public class UsageQueries {

    private final UnitOfWork unitOfWork;
    private final UsageEventReadRepository events;
    private final CustomerLookup customers;

    public UsageQueries(UnitOfWork unitOfWork, UsageEventReadRepository events, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.customers = customers;
    }

    public List<UsageEventView> history(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit) {
        return history(customerId, metric, from, to, before, limit, null);
    }

    /** Newest first; {@code metric}, {@code from}, {@code to} and {@code before} are optional filters. */
    public List<UsageEventView> history(
            CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit, java.util.UUID caller) {
        if (limit < 1) {
            throw new IllegalArgumentException("limit must be at least 1");
        }
        requireWindow(from, to);
        return unitOfWork.transactional(() -> {
            requireCustomer(customerId, caller);
            return events.page(customerId, metric, from, to, before, PageSize.of(limit));
        });
    }

    public List<UsageRecord> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit) {
        requireWindow(from, to);
        if (from == null || to == null) {
            throw new IllegalArgumentException("a period needs both from and to");
        }
        return unitOfWork.transactional(() -> events.find(customerId, metrics, from, to, limit).stream().map(UsageRecordView::toRecord).toList());
    }

    public List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to, java.util.UUID caller) {
        requireWindow(from, to);
        if (from == null || to == null) {
            throw new IllegalArgumentException("a period needs both from and to");
        }
        return unitOfWork.transactional(() -> {
            requireCustomer(customerId, caller);
            return events.totals(customerId, metrics, from, to).stream().map(UsageTotalView::toTotal).toList();
        });
    }

    private void requireCustomer(CustomerId customerId, java.util.UUID caller) {
        customers.findFor(customerId, caller).orElseThrow(() -> new NotFoundException("customer not found"));
    }

    private static void requireWindow(Instant from, Instant to) {
        if (from != null && to != null && !from.isBefore(to)) {
            throw new IllegalArgumentException("from must be before to");
        }
    }
}
