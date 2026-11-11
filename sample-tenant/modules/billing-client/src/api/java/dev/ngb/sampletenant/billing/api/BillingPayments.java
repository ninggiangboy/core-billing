package dev.ngb.sampletenant.billing.api;

import dev.ngb.sampletenant.billing.api.model.Payment;
import dev.ngb.sampletenant.billing.api.model.PaymentDetail;
import java.util.List;

/** Payments, their history, and starting a wallet top-up through Stripe Checkout. */
public interface BillingPayments {

    record TopUpRequest(String walletId, long amount, String currency) {}

    List<Payment> payments(String walletId, String status, int limit);

    PaymentDetail payment(String id);

    Payment createTopUp(String idempotencyKey, TopUpRequest request);
}
