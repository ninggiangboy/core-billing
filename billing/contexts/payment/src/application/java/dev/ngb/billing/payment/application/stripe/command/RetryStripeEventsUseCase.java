package dev.ngb.billing.payment.application.stripe.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.payment.application.payment.service.PaymentSettings;
import dev.ngb.billing.payment.application.stripe.port.StripeEventRepository;
import dev.ngb.billing.payment.application.stripe.service.StripeEventProcessor;
import dev.ngb.billing.payment.domain.stripe.EventStatus;
import dev.ngb.billing.payment.domain.stripe.StripeEventId;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * The retry worker for the current tenant: claims the events whose retry time has come, then tries them. Several
 * workers may run this at once; each claims a different set, and a claim another worker holds is invisible.
 */
public class RetryStripeEventsUseCase {

    /** An event stored this long ago and still untouched was left behind by a stopped process. */
    private static final Duration UNPROCESSED_AFTER = Duration.ofMinutes(2);

    /**
     * How long a worker owns a claimed event before another may take it over. One attempt is a Stripe read plus a
     * short transaction, so this outlasts it, and a worker that dies mid-attempt costs about this much.
     */
    private static final Duration CLAIM_LEASE = Duration.ofMinutes(2);

    /** {@code tried} is what this round claimed; {@code skipped} were claimed but settled by another worker first. */
    public record Round(int tried, int processed, int stillFailing, int dead, int skipped) {}

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
        Instant now = clock.now();
        List<StripeEventId> claimed = unitOfWork.transactional(
                () -> events.claimDue(now, now.minus(UNPROCESSED_AFTER), CLAIM_LEASE, settings.eventBatchSize()));
        int processed = 0;
        int failing = 0;
        int dead = 0;
        int skipped = 0;
        for (StripeEventId id : claimed) {
            StripeEventProcessor.Outcome outcome = processor.attempt(id);
            if (!outcome.attempted()) {
                // Another worker settled it between the claim and this attempt, so this one spent no attempt.
                skipped++;
            } else if (outcome.status() == EventStatus.PROCESSED || outcome.status() == EventStatus.IGNORED) {
                processed++;
            } else if (outcome.status() == EventStatus.DEAD) {
                dead++;
            } else {
                failing++;
            }
        }
        return new Round(claimed.size(), processed, failing, dead, skipped);
    }
}
