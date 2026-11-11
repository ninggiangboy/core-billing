package dev.ngb.core_billing.payment.application.payment.query;

import dev.ngb.core_billing.payment.domain.payment.PaymentStatus;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of payments and their failed attempts. */
public interface PaymentReadRepository {

    /** Newest first; {@code before} is the id of the last payment of the previous page. */
    List<PaymentView> list(WalletId walletId, PaymentStatus status, UUID before, int limit, List<UUID> customerIds);

    Optional<PaymentView> findById(UUID id);

    List<PaymentAttemptView> attempts(UUID paymentId);
}
