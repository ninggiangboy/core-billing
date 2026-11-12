package dev.ngb.billing.invoicing.application.invoice.service;

import dev.ngb.core.event.EventPublisher;
import dev.ngb.core.runtime.Clock;
import dev.ngb.core.runtime.IdGenerator;
import dev.ngb.billing.invoicing.application.invoice.port.InvoiceRepository;
import dev.ngb.billing.invoicing.domain.invoice.Invoice;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceEvents;
import dev.ngb.billing.invoicing.domain.invoice.InvoiceNumber;
import java.time.Instant;

/**
 * The one place an invoice is numbered, opened, stored and announced, whatever created it. It must run inside the
 * caller's transaction: the number comes from a locked counter row, so the numbers stay gap-free only if a rolled
 * back invoice also rolls back its number, and the outbox rows commit with the invoice they report.
 */
public class InvoiceIssuer {

    private final InvoiceRepository invoices;
    private final EventPublisher events;
    private final IdGenerator ids;
    private final Clock clock;

    public InvoiceIssuer(InvoiceRepository invoices, EventPublisher events, IdGenerator ids, Clock clock) {
        this.invoices = invoices;
        this.events = events;
        this.ids = ids;
        this.clock = clock;
    }

    /**
     * @param settledByWallet true when the wallet already paid every charge on the invoice, so it opens and is paid in one step
     */
    public Invoice issue(Invoice draft, boolean settledByWallet) {
        Instant now = clock.now();
        draft.issue(InvoiceNumber.format(invoices.nextNumber(draft.tenantId())), now);
        if (settledByWallet) {
            draft.settleFromWallet(now);
        }
        invoices.add(draft);
        events.publish(new InvoiceEvents.Created(ids.newId(), draft, now));
        if (settledByWallet) {
            events.publish(new InvoiceEvents.Paid(ids.newId(), draft, null, now));
        }
        return draft;
    }
}
