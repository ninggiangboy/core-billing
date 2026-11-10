package io.corebilling.boot.jobs;

import io.corebilling.payment.application.stripe.command.ReconcileStripeUseCase;
import io.corebilling.payment.application.stripe.command.RetryStripeEventsUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work of the payment module: retrying Stripe events that failed, and comparing payments and
 * refunds with Stripe. Switching it off with {@code billing.payments.jobs-enabled=false} stops the polling only;
 * webhooks are still received and processed when they arrive.
 */
@Component
@ConditionalOnProperty(name = {"billing.jobs.enabled", "billing.payments.jobs-enabled"}, havingValue = "true", matchIfMissing = true)
public class PaymentJobs {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentJobs.class);

    private final TenantSweep sweep;
    private final RetryStripeEventsUseCase retry;
    private final ReconcileStripeUseCase reconcile;
    private final MeterRegistry meters;

    PaymentJobs(TenantSweep sweep, RetryStripeEventsUseCase retry, ReconcileStripeUseCase reconcile, MeterRegistry meters) {
        this.sweep = sweep;
        this.retry = retry;
        this.reconcile = reconcile;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${billing.payments.event-retry-delay:PT15S}")
    public void retryFailedEvents() {
        sweep.forEachActiveTenant(tenant -> {
            RetryStripeEventsUseCase.Round round = retry.runOnce();
            if (round.dead() > 0) {
                meters.counter("billing.stripe.event", "outcome", "dead").increment(round.dead());
            }
            if (round.processed() > 0) {
                meters.counter("billing.stripe.event", "outcome", "processed-on-retry").increment(round.processed());
            }
        });
    }

    @Scheduled(fixedDelayString = "${billing.payments.reconcile-delay:PT1H}", initialDelayString = "${billing.payments.reconcile-delay:PT1H}")
    public void reconcileWithStripe() {
        sweep.forEachActiveTenant(tenant -> {
            ReconcileStripeUseCase.Report report = reconcile.runOnce();
            if (report.mismatches() > 0) {
                LOG.warn("stripe reconciliation for tenant {} found {} mismatches, {} corrected", tenant, report.mismatches(), report.corrected());
                meters.counter("billing.stripe.reconcile.mismatch").increment(report.mismatches());
            }
        });
    }
}
