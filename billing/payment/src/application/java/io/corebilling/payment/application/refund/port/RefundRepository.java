package io.corebilling.payment.application.refund.port;

import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundId;
import io.corebilling.sharedkernel.Money;
import java.util.Optional;

/** Refunds of the current tenant. */
public interface RefundRepository {

    void add(Refund refund);

    void update(Refund refund);

    Optional<Refund> findById(RefundId id);

    Optional<Refund> lockById(RefundId id);

    Optional<Refund> findByStripeRefundId(String stripeRefundId);

    Optional<Refund> findByIdempotencyKey(String key);

    /** Refunds that count against the captured amount: those still pending and those that succeeded. */
    long liveAmount(PaymentId paymentId);
}
