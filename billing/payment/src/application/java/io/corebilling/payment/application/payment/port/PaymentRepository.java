package io.corebilling.payment.application.payment.port;

import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentAttempt;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import java.util.Optional;

/** Payments of the current tenant. The {@code lock...} methods take the row lock and must run in a transaction. */
public interface PaymentRepository {

    void add(Payment payment);

    void update(Payment payment);

    Optional<Payment> findById(PaymentId id);

    Optional<Payment> lockById(PaymentId id);

    Optional<Payment> lockByCheckoutSession(String sessionId);

    Optional<Payment> lockByPaymentIntent(String intentId);

    Optional<Payment> findByIdempotencyKey(String key);

    /** Newest first; {@code before} is the id of the last payment of the previous page. */
    List<Payment> list(WalletId walletId, PaymentStatus status, PaymentId before, int limit);

    /** Records a failed card attempt once per event; repeats are ignored. */
    void addAttempt(PaymentId id, PaymentAttempt attempt);

    List<PaymentAttempt> attempts(PaymentId id);
}
