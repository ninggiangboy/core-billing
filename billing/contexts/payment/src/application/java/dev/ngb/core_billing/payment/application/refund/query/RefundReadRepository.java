package dev.ngb.core_billing.payment.application.refund.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of refunds. */
public interface RefundReadRepository {

    List<RefundView> byPayment(UUID paymentId);

    Optional<RefundView> findById(UUID id);

    /** Newest first; {@code before} is the id of the last refund of the previous page. */
    List<RefundView> listByCustomer(UUID customerId, UUID before, int limit);
}
