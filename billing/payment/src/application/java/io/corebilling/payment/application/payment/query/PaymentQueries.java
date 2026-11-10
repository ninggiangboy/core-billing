package io.corebilling.payment.application.payment.query;

import io.corebilling.payment.application.refund.query.RefundReadRepository;
import io.corebilling.payment.application.refund.query.RefundView;
import io.corebilling.payment.domain.payment.PaymentStatus;
import io.corebilling.platform.api.error.NotFoundException;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import java.util.UUID;

/** Read access to the current tenant's payments and refunds, through the read models. */
public class PaymentQueries {

    public record PaymentDetail(PaymentView payment, List<RefundView> refunds, List<PaymentAttemptView> attempts) {}

    private final UnitOfWork unitOfWork;
    private final PaymentReadRepository payments;
    private final RefundReadRepository refunds;

    public PaymentQueries(UnitOfWork unitOfWork, PaymentReadRepository payments, RefundReadRepository refunds) {
        this.unitOfWork = unitOfWork;
        this.payments = payments;
        this.refunds = refunds;
    }

    public List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit) {
        return unitOfWork.transactional(() -> payments.list(walletId, status, before, Math.max(1, Math.min(limit, 200))));
    }

    public PaymentDetail payment(UUID id) {
        return unitOfWork.transactional(() -> payments.findById(id)
                .map(payment -> new PaymentDetail(payment, refunds.byPayment(id), payments.attempts(id)))
                .orElseThrow(() -> new NotFoundException("payment not found")));
    }
}
