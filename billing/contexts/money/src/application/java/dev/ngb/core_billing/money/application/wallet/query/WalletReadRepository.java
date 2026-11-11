package dev.ngb.core_billing.money.application.wallet.query;

import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.List;
import java.util.Optional;

/** Read-only projections of wallets. */
public interface WalletReadRepository {

    Optional<WalletView> findById(WalletId id);

    List<WalletView> listByCustomer(CustomerId customerId);
}
