package dev.ngb.core_billing.invoicing.application.subscription.port;

import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionHistoryEntry;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import java.time.Instant;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface SubscriptionRepository {

    void add(Subscription subscription);

    void update(Subscription subscription);

    Optional<Subscription> findById(SubscriptionId id);

    /** Loads the subscription and holds its row until the transaction ends. */
    Optional<Subscription> lockById(SubscriptionId id);

    /**
     * The subscription whose renewal is most overdue at {@code now}, locked until the transaction ends. A row another
     * worker holds is skipped, so two workers never renew the same subscription. Rows in {@code excluding} are passed
     * over too, so a subscription whose lock timed out is not tried again for the rest of the tick.
     */
    Optional<Subscription> lockNextDue(Instant now, Set<UUID> excluding);

    Optional<Subscription> findByIdempotencyKey(String idempotencyKey);

    /** Appends to the history. An event is never changed or removed. */
    void appendEvent(SubscriptionHistoryEntry event);
}
