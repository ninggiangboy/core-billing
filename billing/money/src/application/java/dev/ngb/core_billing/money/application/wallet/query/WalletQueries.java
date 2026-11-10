package dev.ngb.core_billing.money.application.wallet.query;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.application.charge.query.ChargeReadRepository;
import dev.ngb.core_billing.money.application.charge.query.ChargeView;
import dev.ngb.core_billing.money.application.ledger.query.LedgerEntryView;
import dev.ngb.core_billing.money.application.ledger.query.LedgerReadRepository;
import dev.ngb.core_billing.money.application.reservation.query.ReservationReadRepository;
import dev.ngb.core_billing.money.application.reservation.query.ReservationView;
import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.CustomerId;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.util.List;

/** Read side of the money module, within the current tenant. */
public class WalletQueries {

    public static final int MAX_PAGE = 200;

    private final UnitOfWork unitOfWork;
    private final WalletReadRepository wallets;
    private final LedgerReadRepository ledger;
    private final ReservationReadRepository reservations;
    private final ChargeReadRepository charges;

    public WalletQueries(
            UnitOfWork unitOfWork, WalletReadRepository wallets, LedgerReadRepository ledger, ReservationReadRepository reservations,
            ChargeReadRepository charges) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.ledger = ledger;
        this.reservations = reservations;
        this.charges = charges;
    }

    public WalletView wallet(WalletId id) {
        return unitOfWork.transactional(() -> wallets.findById(id).orElseThrow(() -> new NotFoundException("wallet not found")));
    }

    public List<WalletView> walletsOf(CustomerId customerId) {
        return unitOfWork.transactional(() -> wallets.listByCustomer(customerId));
    }

    /** Entries after {@code afterSequence}, oldest first, at most {@link #MAX_PAGE}. */
    public List<LedgerEntryView> ledger(WalletId walletId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1) {
            throw new IllegalArgumentException("afterSequence must not be negative and limit must be at least 1");
        }
        return unitOfWork.transactional(() -> {
            wallets.findById(walletId).orElseThrow(() -> new NotFoundException("wallet not found"));
            return ledger.page(walletId, afterSequence, Math.min(limit, MAX_PAGE));
        });
    }

    public ReservationView reservation(ReservationId id) {
        return unitOfWork.transactional(() -> reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found")));
    }

    public ChargeView charge(ChargeId id) {
        return unitOfWork.transactional(() -> charges.findById(id).orElseThrow(() -> new NotFoundException("charge not found")));
    }
}
