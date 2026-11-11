package dev.ngb.core_billing.invoicing.application.invoice.command;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.invoicing.application.cycle.port.UnbilledLineRepository;
import dev.ngb.core_billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.core_billing.invoicing.domain.cycle.UnbilledLine;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceItem;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceOrigin;
import dev.ngb.core_billing.invoicing.domain.invoice.ItemSource;
import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeLookup;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.charge.ChargeSummary;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletMode;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.platform.api.audit.Audit;
import dev.ngb.core_billing.platform.api.audit.AuditEntry;
import dev.ngb.core_billing.platform.api.audit.AuditLog;
import dev.ngb.core_billing.platform.api.error.ConflictException;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.platform.api.tenant.TenantContext;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Issues an invoice for one succeeded charge, on a Tenant's request, with the same aggregate and numbering as a
 * cycle invoice. A charge is billed on one line at most, so asking again returns the invoice that already exists.
 * A Postpaid charge is still owed, so its invoice opens for payment (and a line already waiting for its cycle moves
 * onto it); a Prepaid charge was paid from the wallet when it was made, so its invoice is a paid record.
 */
public class InvoiceChargeUseCase {

    public record Command(ChargeId chargeId, Audit audit, UUID caller) {}

    /** {@code replayed} is true when the charge already had an invoice and that one is returned. */
    public record Result(Invoice invoice, boolean replayed) {}

    private final UnitOfWork unitOfWork;
    private final ChargeLookup charges;
    private final WalletLookup wallets;
    private final CustomerLookup customers;
    private final InvoiceRepository invoices;
    private final UnbilledLineRepository unbilled;
    private final InvoiceIssuer issuer;
    private final AuditLog auditLog;
    private final TenantContext tenantContext;
    private final IdGenerator ids;
    private final Clock clock;

    public InvoiceChargeUseCase(
            UnitOfWork unitOfWork, ChargeLookup charges, WalletLookup wallets, CustomerLookup customers, InvoiceRepository invoices,
            UnbilledLineRepository unbilled, InvoiceIssuer issuer, AuditLog auditLog, TenantContext tenantContext, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.charges = charges;
        this.wallets = wallets;
        this.customers = customers;
        this.invoices = invoices;
        this.unbilled = unbilled;
        this.issuer = issuer;
        this.auditLog = auditLog;
        this.tenantContext = tenantContext;
        this.ids = ids;
        this.clock = clock;
    }

    public Result execute(Command command) {
        try {
            return unitOfWork.transactional(() -> invoice(command));
        } catch (ConflictException billedMeanwhile) {
            // Another request, or the cycle job, billed the charge between our check and our insert.
            return unitOfWork.transactional(() -> invoices.findByCharge(command.chargeId().value()).map(existing -> new Result(existing, true))
                    .orElseThrow(() -> billedMeanwhile));
        }
    }

    private Result invoice(Command command) {
        ChargeSummary charge = charges.find(command.chargeId()).orElseThrow(() -> new NotFoundException("charge not found"));
        WalletSummary wallet = wallets.find(charge.walletId()).orElseThrow(() -> new NotFoundException("charge not found"));
        customers.findFor(wallet.customerId(), command.caller()).orElseThrow(() -> new NotFoundException("charge not found"));
        Optional<Invoice> existing = invoices.findByCharge(command.chargeId().value());
        if (existing.isPresent()) {
            return new Result(existing.get(), true);
        }
        if (charge.status() != ChargeStatus.SUCCEEDED) {
            throw new ConflictException("a failed charge cannot be invoiced");
        }
        Instant now = clock.now();
        Optional<UnbilledLine> waiting = unbilled.lockByCharge(charge.id().value());
        InvoiceItem item = waiting.map(UnbilledLine::item).orElseGet(() -> new InvoiceItem(ids.newId(), ItemSource.CHARGE, charge.id().value(), null, null,
                null, ChargeDescription.of(charge.source().name(), charge.reference()), charge.amount(), charge.createdAt()));
        Invoice draft = Invoice.draft(new InvoiceId(ids.newId()), tenantContext.requireTenant(), wallet.customerId(), wallet.id(), wallet.currency(),
                InvoiceOrigin.CHARGE, List.of(item), null, null, now);
        Invoice issued = issuer.issue(draft, wallet.mode() == WalletMode.PREPAID);
        auditLog.record(AuditEntry.tenant(issued.tenantId(), command.audit().actor(), "invoice.issued-for-charge", "invoice/" + issued.id(),
                command.audit().requestId(), now));
        return new Result(issued, false);
    }
}
