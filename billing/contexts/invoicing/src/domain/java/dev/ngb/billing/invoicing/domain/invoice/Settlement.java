package dev.ngb.billing.invoicing.domain.invoice;

/** What paid an invoice. Only these two exist: nothing marks an invoice paid on a caller's say-so. */
public enum Settlement {
    /** The wallet already paid the charge, so the invoice is a record of it. */
    WALLET,
    /** A verified payment succeeded for the invoice. */
    PAYMENT
}
