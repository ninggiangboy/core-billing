package dev.ngb.billing.payment.application.stripe.query;

import dev.ngb.billing.payment.domain.stripe.EventStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of Stripe events and their attempt history. */
public interface StripeEventReadRepository {

    /** Newest first; {@code before} is the id of the last event of the previous page. */
    List<StripeEventView> list(EventStatus status, UUID before, int limit);

    Optional<StripeEventView> findById(UUID id);

    List<EventAttemptView> attempts(UUID eventId);
}
