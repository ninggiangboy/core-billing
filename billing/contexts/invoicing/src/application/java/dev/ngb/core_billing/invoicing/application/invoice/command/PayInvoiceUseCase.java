package dev.ngb.core_billing.invoicing.application.invoice.command;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePaymentResult;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePayment;
import dev.ngb.core_billing.payment.api.invoice.StartInvoicePaymentCommand;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.idempotency.Fingerprint;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.runtime.Clock;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
import java.util.UUID;

/**
 * Starts a payment for an open invoice and returns the Stripe-hosted page where the customer pays. Nothing marks the
 * invoice paid here: that happens only when the payment context announces a verified success (see
 * {@link ApplyPaymentUseCase}). The payment is created outside any transaction of this context, because it calls
 * Stripe, so the invoice row is never locked across a network call.
 */
public class PayInvoiceUseCase {

    public record Command(InvoiceId invoiceId, String idempotencyKey, Audit audit, UUID caller) {}

    public record Result(Invoice invoice, StartInvoicePaymentResult payment) {}

    private final UnitOfWork unitOfWork;
    private final InvoiceRepository invoices;
    private final CustomerLookup customers;
    private final StartInvoicePayment payments;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final Clock clock;

    public PayInvoiceUseCase(
            UnitOfWork unitOfWork, InvoiceRepository invoices, CustomerLookup customers, StartInvoicePayment payments, AuditLog auditLog,
            TenantContext tenantContext, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.invoices = invoices;
        this.customers = customers;
        this.payments = payments;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.clock = clock;
    }

    public Result execute(Command command) {
        String key = Fingerprint.requireKey(command.idempotencyKey());
        Invoice invoice = unitOfWork.transactional(() -> payable(command));
        StartInvoicePaymentResult started = payments.start(new StartInvoicePaymentCommand(invoice.id().value(), invoice.walletId(), invoice.customerId(),
                invoice.total(), "Invoice " + invoice.number(), key));
        return unitOfWork.transactional(() -> {
            Invoice locked = invoices.lockById(command.invoiceId()).orElseThrow(() -> new NotFoundException("invoice not found"));
            if (!locked.status().payable() && locked.payments().stream().noneMatch(p -> p.paymentId().equals(started.paymentId()))) {
                throw new ConflictException("the invoice is " + locked.status() + " and can no longer be paid");
            }
            if (locked.status().payable()) {
                locked.startPayment(started.paymentId(), clock.now());
                invoices.update(locked);
                if (!started.replayed()) {
                    auditLog.record(AuditEntry.tenant(tenantContext.requireTenant(), command.audit().actor(), "invoice.payment-started",
                            "invoice/" + locked.id(), command.audit().requestId(), clock.now()));
                }
            }
            return new Result(locked, started);
        });
    }

    private Invoice payable(Command command) {
        Invoice invoice = invoices.findById(command.invoiceId()).orElseThrow(() -> new NotFoundException("invoice not found"));
        customers.findFor(invoice.customerId(), command.caller()).orElseThrow(() -> new NotFoundException("invoice not found"));
        if (!invoice.status().payable()) {
            throw new ConflictException("the invoice is " + invoice.status() + " and cannot be paid");
        }
        return invoice;
    }
}
