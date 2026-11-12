package dev.ngb.billing.money.application.wallet.port;

import dev.ngb.billing.money.domain.wallet.Wallet;
import dev.ngb.billing.sharedkernel.money.Currency;
import dev.ngb.billing.sharedkernel.id.CustomerId;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.util.Optional;

/**
 * Wallets are always changed under their row lock: {@link #lockById} for the normal path, {@link #tryLockById}
 * for background work that should skip a wallet somebody else is using.
 */
public interface WalletRepository {

    /** @throws dev.ngb.billing.platform.api.error.ConflictException when the customer already has a wallet in that currency */
    void add(Wallet wallet);

    /** Writes the wallet's balance, held total and last sequence. */
    void update(Wallet wallet);

    Optional<Wallet> findById(WalletId id);

    /** Reads and locks the wallet until the transaction ends. */
    Optional<Wallet> lockById(WalletId id);

    /** Like {@link #lockById} but returns empty when the wallet is locked by someone else. */
    Optional<Wallet> tryLockById(WalletId id);

    Optional<Wallet> findByCustomer(CustomerId customerId, Currency currency);
}
