package dev.ngb.billing.payment.application.refund.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core.util.PageSize;
import dev.ngb.billing.customer.api.lookup.CustomerLookup;
import dev.ngb.billing.payment.application.payment.query.PaymentReadRepository;
import dev.ngb.billing.platform.api.error.NotFoundException;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import java.util.List;
import java.util.UUID;

/** Reads refunds for the GET handler. */
public class RefundQueries {

    private final UnitOfWork unitOfWork;
    private final RefundReadRepository refunds;
    private final PaymentReadRepository payments;
    private final CustomerLookup customers;

    public RefundQueries(
            UnitOfWork unitOfWork, RefundReadRepository refunds, PaymentReadRepository payments, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.refunds = refunds;
        this.payments = payments;
        this.customers = customers;
    }

    public RefundView get(UUID id) {
        return get(id, null);
    }

    /** Refunds of one customer's payments, newest first, for the portal principal that is fixed to that customer. */
    public List<RefundView> listForCustomer(CustomerId customerId, UUID before, int limit) {
        return unitOfWork.transactional(() -> refunds.listByCustomer(customerId.value(), before, PageSize.of(limit)));
    }

    public RefundView get(UUID id, UUID caller) {
        return unitOfWork.transactional(() -> {
            RefundView refund = refunds.findById(id).orElseThrow(() -> new NotFoundException("refund not found"));
            if (caller != null) {
                var payment = payments.findById(refund.paymentId()).orElseThrow(() -> new NotFoundException("refund not found"));
                if (customers.findFor(payment.customerId(), caller).isEmpty()) {
                    throw new NotFoundException("refund not found");
                }
            }
            return refund;
        });
    }
}
