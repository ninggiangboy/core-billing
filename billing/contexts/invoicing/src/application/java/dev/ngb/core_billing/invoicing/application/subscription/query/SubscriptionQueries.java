package dev.ngb.core_billing.invoicing.application.subscription.query;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.PageSize;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Read access to the current tenant's subscriptions, through the read models. */
public class SubscriptionQueries {

    public record SubscriptionDetail(SubscriptionView subscription, List<SubscriptionHistoryEntryView> history) {}

    private final UnitOfWork unitOfWork;
    private final SubscriptionReadRepository subscriptions;
    private final CustomerLookup customers;

    public SubscriptionQueries(UnitOfWork unitOfWork, SubscriptionReadRepository subscriptions, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.subscriptions = subscriptions;
        this.customers = customers;
    }

    /** A Tenant System sees only the subscriptions of the customers it mapped; any other caller sees the whole tenant. */
    public List<SubscriptionView> list(UUID customerId, UUID walletId, SubscriptionStatus status, UUID before, int limit, UUID caller) {
        return unitOfWork.transactional(() -> {
            List<UUID> visible = null;
            if (caller != null) {
                List<CustomerId> mapped = customers.mappedTo(caller);
                if (mapped.isEmpty()) {
                    return List.of();
                }
                visible = mapped.stream().map(CustomerId::value).toList();
            }
            return subscriptions.list(customerId, walletId, status, before, PageSize.of(limit), visible);
        });
    }

    public SubscriptionDetail get(UUID id, UUID caller) {
        return unitOfWork.transactional(() -> {
            SubscriptionView subscription = subscriptions.findById(id).orElseThrow(() -> new NotFoundException("subscription not found"));
            if (caller != null && customers.findFor(subscription.customerId(), caller).isEmpty()) {
                throw new NotFoundException("subscription not found");
            }
            return new SubscriptionDetail(subscription, subscriptions.history(id));
        });
    }
}
