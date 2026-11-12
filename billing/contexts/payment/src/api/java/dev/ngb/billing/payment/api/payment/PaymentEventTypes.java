package dev.ngb.billing.payment.api.payment;

/**
 * The published names of the payment events other contexts react to: a consumer compares the type of an outbox event
 * with one of these constants, never with a literal. The schemas in {@code contracts/events} define the payloads.
 */
public final class PaymentEventTypes {

    /** A top-up was paid, or an invoice payment was paid. */
    public static final String PAYMENT_SUCCEEDED = "payment.succeeded";

    /** A top-up ended without money. */
    public static final String PAYMENT_FAILED = "payment.failed";

    private PaymentEventTypes() {}
}
