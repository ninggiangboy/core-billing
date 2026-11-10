package io.corebilling.money.application.wallet.port;

import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import java.util.Optional;

/**
 * Wallets are always changed under their row lock: {@link #lockById} for the normal path, {@link #tryLockById}
 * for background work that should skip a wallet somebody else is using.
 */
public interface WalletRepository {

    /** @throws io.corebilling.platform.api.error.ConflictException when the customer already has a wallet in that currency */
    void add(Wallet wallet);

    /** Writes the wallet's balance, held total and last sequence. */
    void update(Wallet wallet);

    Optional<Wallet> findById(WalletId id);

    /** Reads and locks the wallet until the transaction ends. */
    Optional<Wallet> lockById(WalletId id);

    /** Like {@link #lockById} but returns empty when the wallet is locked by someone else. */
    Optional<Wallet> tryLockById(WalletId id);

    Optional<Wallet> findByCustomer(CustomerId customerId, Currency currency);

    List<Wallet> listByCustomer(CustomerId customerId);
}
