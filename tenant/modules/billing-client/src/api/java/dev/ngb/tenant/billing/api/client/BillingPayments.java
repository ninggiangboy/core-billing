package dev.ngb.tenant.billing.api.client;

import dev.ngb.tenant.billing.api.model.Payment;
import dev.ngb.tenant.billing.api.model.PaymentDetail;
import java.util.List;

/** Payments, their history, and starting a wallet top-up through Stripe Checkout. */
public interface BillingPayments {

    record TopUpRequest(String walletId, long amount, String currency) {}

    List<Payment> payments(String walletId, String status, int limit);

    PaymentDetail payment(String id);

    Payment createTopUp(String idempotencyKey, TopUpRequest request);
}
