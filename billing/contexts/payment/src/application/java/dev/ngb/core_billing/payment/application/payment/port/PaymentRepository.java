package dev.ngb.core_billing.payment.application.payment.port;

import dev.ngb.core_billing.payment.domain.payment.Payment;
import dev.ngb.core_billing.payment.domain.payment.PaymentAttempt;
import dev.ngb.core_billing.payment.domain.payment.PaymentId;
import java.util.List;
import java.util.Optional;

/** Payments of the current tenant. The {@code lock...} methods take the row lock and must run in a transaction. */
public interface PaymentRepository {

    /**
     * Inserts the payment unless this tenant already has one with its idempotency key.
     *
     * @return true when the row was inserted, false when the key already existed
     */
    boolean addIfAbsent(Payment payment);

    void update(Payment payment);

    Optional<Payment> findById(PaymentId id);

    Optional<Payment> lockById(PaymentId id);

    Optional<Payment> lockByCheckoutSession(String sessionId);

    Optional<Payment> lockByPaymentIntent(String intentId);

    Optional<Payment> findByIdempotencyKey(String key);

    /** Records a failed card attempt once per event; repeats are ignored. */
    void addAttempt(PaymentId id, PaymentAttempt attempt);

    /** The attempts of a payment; a close decides from the last one whether the card was declined. */
    List<PaymentAttempt> attempts(PaymentId id);
}
