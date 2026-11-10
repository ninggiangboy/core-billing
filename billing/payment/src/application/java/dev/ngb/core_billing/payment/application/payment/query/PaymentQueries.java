package dev.ngb.core_billing.payment.application.payment.query;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.payment.application.refund.query.RefundReadRepository;
import dev.ngb.core_billing.payment.application.refund.query.RefundView;
import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.CustomerId;
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
    private final CustomerLookup customers;

    public PaymentQueries(UnitOfWork unitOfWork, PaymentReadRepository payments, RefundReadRepository refunds, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.payments = payments;
        this.refunds = refunds;
        this.customers = customers;
    }

    public List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit) {
        return list(walletId, status, before, limit, null);
    }

    /** A Tenant System sees only payments of the customers it mapped; any other caller sees the whole tenant. */
    public List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit, UUID caller) {
        return unitOfWork.transactional(() -> {
            List<UUID> visible = null;
            if (caller != null) {
                List<CustomerId> mapped = customers.mappedTo(caller);
                if (mapped.isEmpty()) {
                    return List.of();
                }
                visible = mapped.stream().map(CustomerId::value).toList();
            }
            return payments.list(walletId, status, before, Math.max(1, Math.min(limit, 200)), visible);
        });
    }

    public PaymentDetail payment(UUID id) {
        return payment(id, null);
    }

    public PaymentDetail payment(UUID id, UUID caller) {
        return unitOfWork.transactional(() -> payments.findById(id)
                .map(payment -> {
                    requireVisible(payment, caller);
                    return new PaymentDetail(payment, refunds.byPayment(id), payments.attempts(id));
                })
                .orElseThrow(() -> new NotFoundException("payment not found")));
    }

    private void requireVisible(PaymentView payment, UUID caller) {
        if (caller != null && customers.findFor(payment.customerId(), caller).isEmpty()) {
            throw new NotFoundException("payment not found");
        }
    }
}
