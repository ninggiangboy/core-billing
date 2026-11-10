package io.corebilling.payment.application.payment.query;

import io.corebilling.payment.domain.payment.Payment;
import io.corebilling.payment.domain.payment.PaymentAttempt;
import io.corebilling.payment.domain.payment.PaymentId;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.payment.domain.refund.Refund;
import io.corebilling.payment.domain.refund.RefundId;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import io.corebilling.payment.application.payment.port.PaymentRepository;
import io.corebilling.payment.application.refund.port.RefundRepository;

/** Read access to the current tenant's payments and refunds. */
public class PaymentQueries {

    public record PaymentDetail(Payment payment, List<Refund> refunds, List<PaymentAttempt> attempts) {}

    private final UnitOfWork unitOfWork;
    private final PaymentRepository payments;
    private final RefundRepository refunds;

    public PaymentQueries(UnitOfWork unitOfWork, PaymentRepository payments, RefundRepository refunds) {
        this.unitOfWork = unitOfWork;
        this.payments = payments;
        this.refunds = refunds;
    }

    public List<Payment> list(WalletId walletId, PaymentStatus status, PaymentId before, int limit) {
        return unitOfWork.transactional(() -> payments.list(walletId, status, before, Math.max(1, Math.min(limit, 200))));
    }

    public PaymentDetail payment(PaymentId id) {
        return unitOfWork.transactional(() -> {
            Payment payment = payments.findById(id).orElseThrow(() -> new NotFoundException("payment not found"));
            return new PaymentDetail(payment, refunds.listByPayment(id), payments.attempts(id));
        });
    }

    public Refund refund(RefundId id) {
        return unitOfWork.transactional(() -> refunds.findById(id).orElseThrow(() -> new NotFoundException("refund not found")));
    }
}
