package dev.ngb.core_billing.invoicing.domain.invoice;

public enum ItemSource {
    /** A charge made from usage, a reservation or on its own. */
    CHARGE,
    /** The renewal charge of one subscription period. */
    SUBSCRIPTION_PERIOD
}
