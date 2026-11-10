package io.corebilling.money.application.wallet.query;

import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import java.util.Optional;

/** Read-only projections of wallets. */
public interface WalletReadRepository {

    Optional<WalletView> findById(WalletId id);

    List<WalletView> listByCustomer(CustomerId customerId);
}
