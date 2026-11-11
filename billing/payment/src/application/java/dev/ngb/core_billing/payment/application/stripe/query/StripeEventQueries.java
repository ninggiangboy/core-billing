package dev.ngb.core_billing.payment.application.stripe.query;

import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.PageSize;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Reads Stripe webhook event history for the GET handlers. */
public class StripeEventQueries {


    public record Detail(StripeEventView event, List<EventAttemptView> attempts) {}

    private final UnitOfWork unitOfWork;
    private final StripeEventReadRepository events;

    public StripeEventQueries(UnitOfWork unitOfWork, StripeEventReadRepository events) {
        this.unitOfWork = unitOfWork;
        this.events = events;
    }

    public List<StripeEventView> list(EventStatus status, UUID before, int limit) {
        return unitOfWork.transactional(() -> events.list(status, before, PageSize.of(limit)));
    }

    public Detail get(UUID id) {
        return unitOfWork.transactional(() -> events.findById(id)
                .map(event -> new Detail(event, events.attempts(id)))
                .orElseThrow(() -> new NotFoundException("stripe event not found")));
    }
}
