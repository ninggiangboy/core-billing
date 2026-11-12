package dev.ngb.core_billing.money.application.wallet.service;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core_billing.money.api.wallet.WalletLookup;
import dev.ngb.core_billing.money.api.wallet.WalletSummary;
import dev.ngb.core_billing.money.application.wallet.query.WalletReadRepository;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.Optional;

public class WalletLookupService implements WalletLookup {

    private final UnitOfWork unitOfWork;
    private final WalletReadRepository wallets;

    public WalletLookupService(UnitOfWork unitOfWork, WalletReadRepository wallets) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
    }

    @Override
    public Optional<WalletSummary> find(WalletId id) {
        return unitOfWork.transactional(
                () -> wallets.findById(id).map(w -> new WalletSummary(w.id(), w.customerId(), w.currency(), w.mode())));
    }
}
