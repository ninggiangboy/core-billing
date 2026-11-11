package dev.ngb.core_billing.invoicing.application.invoice.command;

import dev.ngb.core_billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceEvents;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.money.api.ledger.LedgerPostingResult;
import dev.ngb.core_billing.money.api.wallet.CreditCommand;
import dev.ngb.core_billing.money.api.wallet.CreditWallet;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.Clock;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.DomainEvent;
import dev.ngb.core_billing.sharedkernel.EventInbox;
import dev.ngb.core_billing.sharedkernel.EventPublisher;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Consumes the verified payment results of invoice payments ({@code payment.succeeded} and {@code payment.failed}
 * that carry an {@code invoiceId}; top-ups carry none and are ignored). On success the debt payoff is credited to
 * the wallet through the money context, the invoice is marked paid and {@code invoice.paid} is announced, all in
 * the relay's transaction together with the inbox mark, so a repeated event changes nothing. The credit key is
 * derived from the payment, so even a lost inbox mark could not credit twice.
 *
 * <p>Money that arrives for an invoice that cannot be paid any more (already paid by another payment, or void) is
 * still credited to the wallet, because the customer really paid it; the payment is recorded in the invoice history.
 */
public class ApplyPaymentUseCase {

    public static final String CONSUMER = "invoicing.payment";
    public static final String SUCCEEDED = "payment.succeeded";
    public static final String FAILED = "payment.failed";

    private static final String ACTOR = "system/invoice-payment";

    private final UnitOfWork unitOfWork;
    private final EventInbox inbox;
    private final InvoiceRepository invoices;
    private final CreditWallet credit;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public ApplyPaymentUseCase(
            UnitOfWork unitOfWork, EventInbox inbox, InvoiceRepository invoices, CreditWallet credit, EventPublisher events, IdGenerator ids, Clock clock) {
        this.unitOfWork = unitOfWork;
        this.inbox = inbox;
        this.invoices = invoices;
        this.credit = credit;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /** @return true when the invoice was changed */
    public boolean handle(DomainEvent event) {
        if (!SUCCEEDED.equals(event.type()) && !FAILED.equals(event.type())) {
            return false;
        }
        Map<String, Object> data = event.payload();
        if (data.get("invoiceId") == null) {
            return false;
        }
        return unitOfWork.transactional(() -> {
            if (!inbox.firstTime(CONSUMER, event)) {
                return false;
            }
            Invoice invoice = invoices.lockById(InvoiceId.of((String) data.get("invoiceId")))
                    .orElseThrow(() -> new NotFoundException("invoice " + data.get("invoiceId") + " of a payment was not found"));
            UUID paymentId = UUID.fromString((String) data.get("paymentId"));
            Instant now = clock.now();
            if (FAILED.equals(event.type())) {
                Invoice.Change change = invoice.recordPaymentFailed(paymentId, (String) data.get("reason"), now);
                if (change != Invoice.Change.UNCHANGED) {
                    invoices.update(invoice);
                }
                return change != Invoice.Change.UNCHANGED;
            }
            Money amount = amount(data);
            if (!amount.currency().equals(invoice.currency())) {
                throw new IllegalStateException(
                        "a payment of invoice %s is in %s but the invoice is in %s".formatted(invoice.id(), amount.currency(), invoice.currency()));
            }
            LedgerPostingResult posted = credit.credit(new CreditCommand(invoice.walletId(), amount, "invoice/" + invoice.id(),
                    "Payment of invoice " + invoice.number(), "invoice-payoff:" + paymentId, ACTOR));
            Invoice.Change change = invoice.recordPaymentSucceeded(paymentId, posted.entryId().value(), now);
            if (change == Invoice.Change.UNCHANGED) {
                return false;
            }
            invoices.update(invoice);
            if (change == Invoice.Change.PAID) {
                events.publish(new InvoiceEvents.Paid(ids.newId(), invoice, paymentId, now));
            }
            return true;
        });
    }

    @SuppressWarnings("unchecked")
    private static Money amount(Map<String, Object> data) {
        Map<String, Object> amount = (Map<String, Object>) data.get("amount");
        return Money.of(((Number) amount.get("minorUnits")).longValue(), Currency.of((String) amount.get("currency")));
    }
}
