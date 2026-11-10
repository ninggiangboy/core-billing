package dev.ngb.core_billing.payment.application.payment.query;

import dev.ngb.core_billing.payment.application.refund.query.RefundReadRepository;
import dev.ngb.core_billing.payment.application.refund.query.RefundView;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
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
