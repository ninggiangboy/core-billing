package dev.ngb.billing.payment.application.payment.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core.util.PageSize;
import dev.ngb.billing.customer.api.lookup.CustomerLookup;
import dev.ngb.billing.payment.application.refund.query.RefundReadRepository;
import dev.ngb.billing.payment.application.refund.query.RefundView;
import dev.ngb.billing.payment.domain.payment.PaymentStatus;
import dev.ngb.billing.platform.api.error.NotFoundException;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
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
            return payments.list(walletId, status, before, PageSize.of(limit), visible);
        });
    }

    public PaymentDetail payment(UUID id) {
        return payment(id, null);
    }

    /** Payments of one customer, newest first, for the portal principal that is fixed to that customer. */
    public List<PaymentView> listForCustomer(CustomerId customerId, UUID before, int limit) {
        return unitOfWork.transactional(() -> payments.list(null, null, before, PageSize.of(limit), List.of(customerId.value())));
    }

    /** The payment only when it belongs to {@code customerId}. */
    public PaymentDetail paymentForCustomer(UUID id, CustomerId customerId) {
        return unitOfWork.transactional(() -> payments.findById(id)
                .filter(payment -> payment.customerId().equals(customerId))
                .map(payment -> new PaymentDetail(payment, refunds.byPayment(id), payments.attempts(id)))
                .orElseThrow(() -> new NotFoundException("payment not found")));
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
