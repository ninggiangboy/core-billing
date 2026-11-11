package dev.ngb.core_billing.payment.api.invoice;

/**
 * Starts a payment of an invoice through the Tenant's Stripe configuration, for the invoicing context. The payment
 * is stored first, in REQUIRES_ACTION, and Stripe's hosted Checkout is created for it; nothing is credited here.
 * When Stripe's verified result arrives the payment context announces {@code payment.succeeded} carrying the
 * invoice id, and invoicing applies it.
 */
public interface StartInvoicePayment {

    StartInvoicePaymentResult start(StartInvoicePaymentCommand command);
}
