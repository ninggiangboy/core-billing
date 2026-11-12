package dev.ngb.billing.invoicing.application.messaging.service;

/**
 * The names invoicing acts under when no user is behind the change. A name is stored in the ledger entries and the
 * audit log, so it must not change once released.
 */
public final class InvoicingActors {

    /** The actor that credits the wallet when an invoice payment succeeds. */
    public static final String INVOICE_PAYMENT = "system/invoice-payment";

    private InvoicingActors() {}
}
