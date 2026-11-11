package dev.ngb.core_billing.invoicing.application.invoice.query;

import dev.ngb.core_billing.invoicing.domain.invoice.InvoiceStatus;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projection of invoices, their lines and their payment history. */
public interface InvoiceReadRepository {

    /**
     * Newest first. {@code customerIds} restricts the listing to those customers (null for no restriction, an empty list
     * for none), which is how a Tenant System sees only the customers mapped to it.
     */
    List<InvoiceView> list(UUID customerId, UUID walletId, InvoiceStatus status, UUID before, int limit, List<UUID> customerIds);

    Optional<InvoiceView> findById(UUID id);

    List<InvoiceItemView> items(UUID invoiceId);

    List<InvoicePaymentView> payments(UUID invoiceId);
}
