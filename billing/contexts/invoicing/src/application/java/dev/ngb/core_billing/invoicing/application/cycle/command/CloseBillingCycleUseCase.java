package dev.ngb.core_billing.invoicing.application.cycle.command;

import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository.ClosedPeriod;
import dev.ngb.core_billing.invoicing.application.invoice.service.InvoiceIssuer;
import dev.ngb.core_billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.IdGenerator;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.util.List;
import dev.ngb.core_billing.invoicing.application.cycle.service.CycleSettings;

/**
 * Closes every aggregation period that has ended: the unbilled lines of one Postpaid wallet and period become one
 * issued invoice, announced as {@code invoice.created}. Each period is its own transaction, and it locks the lines
 * first, so running the job twice, or on two instances, bills a period once: the second run finds the lines
 * already attached, and a unique invoice per wallet and period backs that up.
 */
public class CloseBillingCycleUseCase {

    /** {@code skipped} periods had nothing left to bill; {@code failed} periods threw and were left for the next run. */
    public record Report(int invoiced, int skipped, int failed) {}

    private final UnitOfWork unitOfWork;
    private final UnbilledLineRepository lines;
    private final InvoiceIssuer issuer;
    private final CycleSettings settings;
    private final IdGenerator ids;
    private final Clock clock;

    public CloseBillingCycleUseCase(
            UnitOfWork unitOfWork, UnbilledLineRepository lines, InvoiceIssuer issuer, CycleSettings settings, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.lines = lines;
        this.issuer = issuer;
        this.settings = settings;
        this.ids = ids;
        this.clock = clock;
    }

    public Report runOnce() {
        List<ClosedPeriod> due = unitOfWork.transactional(() -> lines.closedPeriods(clock.now(), settings.batchSize()));
        int invoiced = 0;
        int skipped = 0;
        int failed = 0;
        for (ClosedPeriod period : due) {
            try {
                if (Boolean.TRUE.equals(unitOfWork.transactional(() -> close(period)))) {
                    invoiced++;
                } else {
                    skipped++;
                }
            } catch (ConflictException billedMeanwhile) {
                skipped++;
            } catch (RuntimeException broken) {
                // One period that cannot be closed must not hold back the periods behind it; it is tried again next run.
                failed++;
            }
        }
        return new Report(invoiced, skipped, failed);
    }

    private boolean close(ClosedPeriod period) {
        List<UnbilledLine> locked = lines.lockPeriod(period.walletId(), period.cycleStart());
        if (locked.isEmpty()) {
            return false;
        }
        UnbilledLine first = locked.get(0);
        Invoice draft = Invoice.draft(new InvoiceId(ids.newId()), first.tenantId(), first.customerId(), first.walletId(), first.currency(),
                InvoiceOrigin.POSTPAID_CYCLE, locked.stream().map(UnbilledLine::item).toList(), period.cycleStart(), period.cycleEnd(), clock.now());
        issuer.issue(draft, false);
        return true;
    }
}
