package dev.ngb.billing.payment.application.refund.port;

import dev.ngb.billing.payment.domain.payment.PaymentId;
import dev.ngb.billing.payment.domain.refund.Refund;
import dev.ngb.billing.payment.domain.refund.RefundId;
import dev.ngb.billing.sharedkernel.money.Money;
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
