package dev.ngb.billing.invoicing.domain.invoice;

public enum InvoiceOrigin {
    /** The charges of one Postpaid wallet in one aggregation period. */
    POSTPAID_CYCLE,
    /** One standalone Charge the Tenant asked to have an invoice for. */
    CHARGE
}
