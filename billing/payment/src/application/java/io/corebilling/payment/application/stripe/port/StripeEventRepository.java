package io.corebilling.payment.application.stripe.port;

import io.corebilling.payment.domain.stripe.StoredEventId;
import io.corebilling.payment.domain.stripe.StripeEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Stripe events of the current tenant and the history of processing them. */
public interface StripeEventRepository {

    /** @return false when an event with the same account and Stripe event id was already stored */
    boolean insertIfAbsent(StripeEvent event);

    void update(StripeEvent event);

    Optional<StripeEvent> findById(StoredEventId id);

    Optional<StripeEvent> lockById(StoredEventId id);

    /**
     * Events to try now, oldest first: failed ones whose retry time has come, and ones stored but never processed
     * since {@code receivedBefore} (the process stopped between storing and processing).
     */
    List<StoredEventId> due(Instant now, Instant receivedBefore, int limit);

    void addAttempt(StripeEvent event, int number, String outcome, String detail, Instant at);
}
