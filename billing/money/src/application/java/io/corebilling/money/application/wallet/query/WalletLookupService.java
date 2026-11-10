package io.corebilling.money.application.wallet.query;

import io.corebilling.money.api.wallet.WalletLookup;
import io.corebilling.money.api.wallet.WalletSummary;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import java.util.Optional;
import io.corebilling.money.application.wallet.port.WalletRepository;

public class WalletLookupService implements WalletLookup {

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;

    public WalletLookupService(UnitOfWork unitOfWork, WalletRepository wallets) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
    }

    @Override
    public Optional<WalletSummary> find(WalletId id) {
        return unitOfWork.transactional(() -> wallets.findById(id).map(w -> new WalletSummary(w.id(), w.customerId(), w.currency(), w.mode())));
    }
}
