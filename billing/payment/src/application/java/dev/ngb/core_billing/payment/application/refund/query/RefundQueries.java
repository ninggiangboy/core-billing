package dev.ngb.core_billing.payment.application.refund.query;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.UUID;

/** Reads refunds for the GET handler. */
public class RefundQueries {

    private final UnitOfWork unitOfWork;
    private final RefundReadRepository refunds;

    public RefundQueries(UnitOfWork unitOfWork, RefundReadRepository refunds) {
        this.unitOfWork = unitOfWork;
        this.refunds = refunds;
    }

    public RefundView get(UUID id) {
        return unitOfWork.transactional(() -> refunds.findById(id).orElseThrow(() -> new NotFoundException("refund not found")));
    }
}
