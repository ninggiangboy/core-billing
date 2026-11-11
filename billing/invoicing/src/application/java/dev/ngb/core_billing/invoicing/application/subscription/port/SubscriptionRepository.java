package dev.ngb.core_billing.invoicing.application.subscription.port;

import dev.ngb.core_billing.invoicing.domain.subscription.Subscription;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionEvent;
import dev.ngb.core_billing.invoicing.domain.subscription.SubscriptionId;
import java.time.Instant;
import java.util.Optional;

public interface SubscriptionRepository {

    void add(Subscription subscription);

    void update(Subscription subscription);

    Optional<Subscription> findById(SubscriptionId id);

    /** Loads the subscription and holds its row until the transaction ends. */
    Optional<Subscription> lockById(SubscriptionId id);

    /**
     * The subscription whose renewal is most overdue at {@code now}, locked until the transaction ends. A row another
     * worker holds is skipped, so two workers never renew the same subscription.
     */
    Optional<Subscription> lockNextDue(Instant now);

    Optional<Subscription> findByIdempotencyKey(String idempotencyKey);

    /** Appends to the history. An event is never changed or removed. */
    void appendEvent(SubscriptionEvent event);
}
