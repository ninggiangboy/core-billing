package dev.ngb.core_billing.payment.api.invoice;

import java.util.Objects;
import java.util.UUID;

/** {@code checkoutUrl} is Stripe's hosted page, present while the customer can still pay. */
public record StartInvoicePaymentResult(UUID paymentId, String status, String checkoutUrl, boolean replayed) {

    public StartInvoicePaymentResult {
        Objects.requireNonNull(paymentId, "paymentId");
        Objects.requireNonNull(status, "status");
    }
}
