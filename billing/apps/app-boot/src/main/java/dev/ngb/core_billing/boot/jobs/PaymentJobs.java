package dev.ngb.core_billing.boot.jobs;

import org.springframework.context.annotation.Profile;
import dev.ngb.core_billing.boot.infrastructure.coordination.JobLeases;
import dev.ngb.core_billing.payment.application.stripe.command.ReconcileStripeUseCase;
import dev.ngb.core_billing.payment.application.stripe.command.RetryStripeEventsUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import java.time.Duration;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work of the payment module: retrying Stripe events that failed, and comparing payments and
 * refunds with Stripe. Switching it off with {@code billing.payments.jobs-enabled=false} stops the polling only;
 * webhooks are still received and processed when they arrive.
 *
 * <p>Reconciliation is a singleton: a lease makes one worker own the round. Reading Stripe happens outside every
 * row lock, and the lease commits on its own, so the round adds no lock edge to a payment or a wallet.
 */
@Profile("worker")
@Component
@ConditionalOnProperty(name = {"billing.jobs.enabled", "billing.payments.jobs-enabled"}, havingValue = "true", matchIfMissing = true)
public class PaymentJobs {

    private static final Logger LOG = LoggerFactory.getLogger(PaymentJobs.class);
    private static final String RECONCILE_LEASE = "stripe-reconcile";
    private static final Duration RECONCILE_TTL = Duration.ofMinutes(30);

    private final TenantSweep sweep;
    private final RetryStripeEventsUseCase retry;
    private final ReconcileStripeUseCase reconcile;
    private final JobLeases leases;
    private final MeterRegistry meters;

    PaymentJobs(
            TenantSweep sweep,
            RetryStripeEventsUseCase retry,
            ReconcileStripeUseCase reconcile,
            JobLeases leases,
            MeterRegistry meters) {
        this.sweep = sweep;
        this.retry = retry;
        this.reconcile = reconcile;
        this.leases = leases;
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
        if (!leases.tryAcquire(RECONCILE_LEASE, RECONCILE_TTL)) {
            LOG.info("stripe reconciliation is already running on another worker");
            return;
        }
        try {
            sweep.forEachActiveTenant(tenant -> {
                if (!leases.renew(RECONCILE_LEASE, RECONCILE_TTL)) {
                    LOG.warn("stripe reconciliation lost its lease before tenant {}", tenant);
                    return;
                }
                ReconcileStripeUseCase.Report report = reconcile.runOnce();
                if (report.mismatches() > 0) {
                    LOG.warn("stripe reconciliation for tenant {} found {} mismatches, {} corrected", tenant, report.mismatches(), report.corrected());
                    meters.counter("billing.stripe.reconcile.mismatch").increment(report.mismatches());
                }
            });
        } finally {
            leases.release(RECONCILE_LEASE);
        }
    }
}
