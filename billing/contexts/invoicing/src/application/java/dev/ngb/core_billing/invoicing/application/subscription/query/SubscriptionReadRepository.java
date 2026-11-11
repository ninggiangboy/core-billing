package dev.ngb.core_billing.invoicing.application.subscription.query;

import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projection of subscriptions and their history. */
public interface SubscriptionReadRepository {

    /** Newest first; {@code customerIds} restricts the listing as it does for invoices. */
    List<SubscriptionView> list(UUID customerId, UUID walletId, SubscriptionStatus status, UUID before, int limit, List<UUID> customerIds);

    Optional<SubscriptionView> findById(UUID id);

    List<SubscriptionEventView> history(UUID subscriptionId);
}
