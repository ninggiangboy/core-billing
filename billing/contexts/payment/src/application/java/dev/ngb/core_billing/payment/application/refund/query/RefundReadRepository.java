package dev.ngb.core_billing.payment.application.refund.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of refunds. */
public interface RefundReadRepository {

    List<RefundView> byPayment(UUID paymentId);

    Optional<RefundView> findById(UUID id);
}
