package dev.ngb.sampletenant.billing.api;

import dev.ngb.sampletenant.billing.api.model.Invoice;
import dev.ngb.sampletenant.billing.api.model.InvoiceDetail;
import java.util.List;

/** Invoice reads for the customer portal and the tenant's own screens. */
public interface BillingInvoices {

    List<Invoice> invoices(String customerId, String walletId, String status, int limit);

    InvoiceDetail invoice(String id);
}
