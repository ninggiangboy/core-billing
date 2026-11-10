package dev.ngb.core_billing.usage.application.query;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.usage.api.usage.UsageEventId;
import dev.ngb.core_billing.usage.api.usage.UsageLookup;
import dev.ngb.core_billing.usage.api.usage.UsageRecord;
import dev.ngb.core_billing.usage.api.usage.UsageTotal;
import java.time.Instant;
import java.util.List;
import java.util.Set;

/** Reads usage of the current tenant: history for the API, and the {@link UsageLookup} other contexts use. */
public class UsageQueries implements UsageLookup {

    public static final int MAX_PAGE = 200;

    private final UnitOfWork unitOfWork;
    private final UsageEventReadRepository events;
    private final CustomerLookup customers;

    public UsageQueries(UnitOfWork unitOfWork, UsageEventReadRepository events, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.customers = customers;
    }

    /** Newest first; {@code metric}, {@code from}, {@code to} and {@code before} are optional filters. */
    public List<UsageEventView> history(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit) {
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
        return unitOfWork.transactional(() -> events.find(customerId, metrics, from, to, limit).stream().map(UsageRecordView::toRecord).toList());
    }

    @Override
    public List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to) {
        requireWindow(from, to);
        if (from == null || to == null) {
            throw new IllegalArgumentException("a period needs both from and to");
        }
        return unitOfWork.transactional(() -> {
            requireCustomer(customerId);
            return events.totals(customerId, metrics, from, to).stream().map(UsageTotalView::toTotal).toList();
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
