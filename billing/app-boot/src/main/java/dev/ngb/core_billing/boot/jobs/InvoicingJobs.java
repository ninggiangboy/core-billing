package dev.ngb.core_billing.boot.jobs;

import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import dev.ngb.core_billing.invoicing.application.subscription.command.RenewSubscriptionUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work of the invoicing module: closing billing periods into invoices, and renewing due subscriptions.
 * Switching it off with {@code billing.invoicing.jobs-enabled=false} stops both; charges stay collected and subscriptions
 * stay due, and both catch up when it is back on.
 */
@Component
@ConditionalOnProperty(name = {"billing.jobs.enabled", "billing.invoicing.jobs-enabled"}, havingValue = "true", matchIfMissing = true)
public class InvoicingJobs {

    private static final Logger LOG = LoggerFactory.getLogger(InvoicingJobs.class);

    private final TenantSweep sweep;
    private final CloseBillingCycleUseCase closeCycle;
    private final RenewSubscriptionUseCase renew;
    private final MeterRegistry meters;

    InvoicingJobs(TenantSweep sweep, CloseBillingCycleUseCase closeCycle, RenewSubscriptionUseCase renew, MeterRegistry meters) {
        this.sweep = sweep;
        this.closeCycle = closeCycle;
        this.renew = renew;
        this.meters = meters;
    }

    @Scheduled(fixedDelayString = "${billing.invoicing.cycle-delay:PT5M}")
    public void closeBillingCycles() {
        sweep.forEachActiveTenant(tenant -> {
            CloseBillingCycleUseCase.Report report = closeCycle.runOnce();
            if (report.invoiced() > 0) {
                LOG.info("closed {} billing periods for tenant {}", report.invoiced(), tenant);
                meters.counter("billing.invoicing.cycle.invoiced").increment(report.invoiced());
            }
            if (report.failed() > 0) {
                LOG.warn("{} billing periods could not be closed for tenant {}", report.failed(), tenant);
                meters.counter("billing.invoicing.cycle.failed").increment(report.failed());
            }
        });
    }

    @Scheduled(fixedDelayString = "${billing.invoicing.renewal-delay:PT1M}")
    public void renewSubscriptions() {
        sweep.forEachActiveTenant(tenant -> {
            RenewSubscriptionUseCase.Report report = renew.runDue();
            if (report.renewed() > 0) {
                meters.counter("billing.subscription.renewed").increment(report.renewed());
            }
            if (report.failed() > 0) {
                LOG.warn("{} subscription renewals could not be charged for tenant {}", report.failed(), tenant);
                meters.counter("billing.subscription.renewal.failed").increment(report.failed());
            }
        });
    }
}
