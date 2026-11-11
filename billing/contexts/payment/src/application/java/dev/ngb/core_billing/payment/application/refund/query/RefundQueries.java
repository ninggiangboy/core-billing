package dev.ngb.core_billing.payment.application.refund.query;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
import dev.ngb.core_billing.payment.application.payment.query.PaymentReadRepository;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.runtime.UnitOfWork;
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
