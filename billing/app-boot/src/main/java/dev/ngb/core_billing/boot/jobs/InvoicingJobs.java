package dev.ngb.core_billing.boot.jobs;

import dev.ngb.core_billing.invoicing.application.cycle.command.CloseBillingCycleUseCase;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Background work of the invoicing module. Switching it off with {@code billing.invoicing.jobs-enabled=false}
 * stops invoices being created from closed periods; the charges stay collected and are invoiced when it is back on.
 */
@Component
@ConditionalOnProperty(name = {"billing.jobs.enabled", "billing.invoicing.jobs-enabled"}, havingValue = "true", matchIfMissing = true)
public class InvoicingJobs {

    private static final Logger LOG = LoggerFactory.getLogger(InvoicingJobs.class);

    private final TenantSweep sweep;
    private final CloseBillingCycleUseCase closeCycle;
    private final MeterRegistry meters;

    InvoicingJobs(TenantSweep sweep, CloseBillingCycleUseCase closeCycle, MeterRegistry meters) {
        this.sweep = sweep;
        this.closeCycle = closeCycle;
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
        });
    }
}
