package dev.ngb.core_billing.invoicing.domain.subscription;

public enum SubscriptionStatus {
    /** Renewing on schedule. */
    ACTIVE,
    /** A period could not be paid. It is retried on a bounded schedule and, once paid, returns to ACTIVE. */
    PAST_DUE,
    /** Ended. It never renews again. */
    CANCELED
}
