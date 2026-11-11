package dev.ngb.core_billing.payment.application.stripe.port;

import dev.ngb.core_billing.payment.domain.stripe.StripeEventId;
import dev.ngb.core_billing.payment.domain.stripe.StripeEvent;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Stripe events of the current tenant and the history of processing them. */
public interface StripeEventRepository {

    /** @return false when an event with the same account and Stripe event id was already stored */
    boolean insertIfAbsent(StripeEvent event);

    void update(StripeEvent event);

    Optional<StripeEvent> findById(StripeEventId id);

    Optional<StripeEvent> lockById(StripeEventId id);

    /**
     * Claims events to try now, oldest first: failed ones whose retry time has come, and ones stored but never
     * processed since {@code receivedBefore} (the process stopped between storing and processing).
     *
     * <p>The claim is a deadline rather than a held lock, so the attempt runs outside this call's transaction. A
     * claimed event is invisible to another worker until the lease passes; after that it is due again, so a worker
     * that dies mid-attempt costs at most {@code lease} and never loses the event. The claim does not touch the
     * retry schedule.
     */
    List<StripeEventId> claimDue(Instant now, Instant receivedBefore, Duration lease, int limit);

    void addAttempt(StripeEvent event, int number, String outcome, String detail, Instant at);
}
