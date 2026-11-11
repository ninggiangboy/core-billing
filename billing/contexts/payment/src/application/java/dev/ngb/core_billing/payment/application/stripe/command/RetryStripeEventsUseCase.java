package dev.ngb.core_billing.payment.application.stripe.command;

import dev.ngb.core_billing.payment.domain.stripe.EventStatus;
import dev.ngb.core_billing.payment.domain.stripe.StripeEventId;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.util.List;
import dev.ngb.core_billing.payment.application.payment.service.PaymentSettings;
import dev.ngb.core_billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.core_billing.payment.application.stripe.service.StripeEventProcessor;

/** The retry worker for the current tenant: processes failed events whose retry time has come. */
public class RetryStripeEventsUseCase {

    /** An event stored this long ago and still untouched was left behind by a stopped process. */
    private static final java.time.Duration UNPROCESSED_AFTER = java.time.Duration.ofMinutes(2);

    public record Round(int tried, int processed, int stillFailing, int dead) {}

    private final UnitOfWork unitOfWork;
    private final StripeEventRepository events;
    private final StripeEventProcessor processor;
    private final PaymentSettings settings;
    private final Clock clock;

    public RetryStripeEventsUseCase(
            UnitOfWork unitOfWork, StripeEventRepository events, StripeEventProcessor processor, PaymentSettings settings, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.events = events;
        this.processor = processor;
        this.settings = settings;
        this.clock = clock;
    }

    public Round runOnce() {
        List<StripeEventId> due = unitOfWork.transactional(() -> events.due(clock.now(), clock.now().minus(UNPROCESSED_AFTER), settings.eventBatchSize()));
        int processed = 0;
        int failing = 0;
        int dead = 0;
        for (StripeEventId id : due) {
            EventStatus status = processor.attempt(id).status();
            if (status == EventStatus.PROCESSED || status == EventStatus.IGNORED) {
                processed++;
            } else if (status == EventStatus.DEAD) {
                dead++;
            } else {
                failing++;
            }
        }
        return new Round(due.size(), processed, failing, dead);
    }
}
