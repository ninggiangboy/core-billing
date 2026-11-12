package dev.ngb.core_billing.invoicing.application.invoice.command;

import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import java.time.Instant;

/**
 * An administrator's decisions about an invoice. Voiding cancels the request for payment, with a reason, and never
 * touches the wallet: what was charged stays charged, and correcting the ledger is a reversal, not a void. A paid
 * invoice cannot be voided. Both decisions are audited.
 */
public class ManageInvoiceUseCase {

    private final UnitOfWork unitOfWork;
    private final InvoiceRepository invoices;
    private final AuditLog auditLog;
    private final Clock clock;

    public ManageInvoiceUseCase(UnitOfWork unitOfWork, InvoiceRepository invoices, AuditLog auditLog, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.invoices = invoices;
        this.auditLog = auditLog;
        this.clock = clock;
    }

    public Invoice voidInvoice(InvoiceId id, String reason, Audit audit) {
        return change(id, audit, "invoice.voided", invoice -> invoice.voidInvoice(reason, clock.now()));
    }

    public Invoice markUncollectible(InvoiceId id, Audit audit) {
        return change(id, audit, "invoice.marked-uncollectible", Invoice::markUncollectible);
    }

    private Invoice change(InvoiceId id, Audit audit, String action, java.util.function.Consumer<Invoice> decision) {
        return unitOfWork.transactional(() -> {
            Invoice invoice = invoices.lockById(id).orElseThrow(() -> new NotFoundException("invoice not found"));
            try {
                decision.accept(invoice);
            } catch (IllegalStateException refused) {
                throw new ConflictException(refused.getMessage());
            }
            invoices.update(invoice);
            Instant now = clock.now();
            auditLog.record(AuditEntry.tenant(invoice.tenantId(), audit.actor(), action, "invoice/" + invoice.id(), audit.requestId(), now));
            return invoice;
        });
    }
}
