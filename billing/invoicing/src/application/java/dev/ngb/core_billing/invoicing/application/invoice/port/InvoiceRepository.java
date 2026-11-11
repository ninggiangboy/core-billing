package dev.ngb.core_billing.invoicing.application.invoice.port;

import dev.ngb.core_billing.invoicing.domain.invoice.Invoice;
import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceId;
import dev.ngb.core_billing.sharedkernel.TenantId;
import java.util.Optional;

public interface InvoiceRepository {

    /** Stores a new invoice and attaches its lines; a line already attached to another invoice is a conflict. */
    void add(Invoice invoice);

    /** Writes the invoice's own state and its payment history. Lines never change once attached. */
    void update(Invoice invoice);

    Optional<Invoice> findById(InvoiceId id);

    /** Loads the invoice and holds its row until the transaction ends. */
    Optional<Invoice> lockById(InvoiceId id);

    /** The next number of the tenant's gap-free sequence. It locks the counter row, so it must run in the transaction that issues the invoice. */
    long nextNumber(TenantId tenantId);
}
