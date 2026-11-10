package io.corebilling.payment.application;

import io.corebilling.payment.domain.PaymentId;
import io.corebilling.payment.domain.Refund;
import io.corebilling.payment.domain.RefundId;
import io.corebilling.sharedkernel.Money;
import java.util.List;
import java.util.Optional;

/** Refunds of the current tenant. */
public interface RefundRepository {

    void add(Refund refund);

    void update(Refund refund);

    Optional<Refund> findById(RefundId id);

    Optional<Refund> lockById(RefundId id);

    Optional<Refund> findByStripeRefundId(String stripeRefundId);

    Optional<Refund> findByIdempotencyKey(String key);

    List<Refund> listByPayment(PaymentId paymentId);

    /** Refunds that count against the captured amount: those still pending and those that succeeded. */
    long liveAmount(PaymentId paymentId);
}
