package dev.ngb.billing.invoicing.domain.invoice;

/**
 * DRAFT is an invoice being assembled and has no number yet. OPEN is issued and unpaid. PAID, VOID and
 * UNCOLLECTIBLE are the ways an invoice leaves OPEN; only an UNCOLLECTIBLE invoice can still be paid late.
 */
public enum InvoiceStatus {
    DRAFT,
    OPEN,
    PAID,
    VOID,
    UNCOLLECTIBLE;

    /** True when a payment may still settle the invoice. */
    public boolean payable() {
        return this == OPEN || this == UNCOLLECTIBLE;
    }
}
