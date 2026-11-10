package io.corebilling.money.application.wallet.query;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.domain.charge.Charge;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.money.domain.reservation.Reservation;
import io.corebilling.money.domain.wallet.Wallet;
import io.corebilling.platform.api.NotFoundException;
import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.sharedkernel.UnitOfWork;
import io.corebilling.sharedkernel.WalletId;
import java.util.List;
import io.corebilling.money.application.charge.port.ChargeRepository;
import io.corebilling.money.application.ledger.port.LedgerRepository;
import io.corebilling.money.application.reservation.port.ReservationRepository;
import io.corebilling.money.application.wallet.port.WalletRepository;

/** Read side of the money module, within the current tenant. */
public class WalletQueries {

    public static final int MAX_PAGE = 200;

    private final UnitOfWork unitOfWork;
    private final WalletRepository wallets;
    private final LedgerRepository ledger;
    private final ReservationRepository reservations;
    private final ChargeRepository charges;

    public WalletQueries(
            UnitOfWork unitOfWork, WalletRepository wallets, LedgerRepository ledger, ReservationRepository reservations, ChargeRepository charges) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.ledger = ledger;
        this.reservations = reservations;
        this.charges = charges;
    }

    public Wallet wallet(WalletId id) {
        return unitOfWork.transactional(() -> wallets.findById(id).orElseThrow(() -> new NotFoundException("wallet not found")));
    }

    public List<Wallet> walletsOf(CustomerId customerId) {
        return unitOfWork.transactional(() -> wallets.listByCustomer(customerId));
    }

    /** Entries after {@code afterSequence}, oldest first, at most {@link #MAX_PAGE}. */
    public List<LedgerEntry> ledger(WalletId walletId, long afterSequence, int limit) {
        if (afterSequence < 0 || limit < 1) {
            throw new IllegalArgumentException("afterSequence must not be negative and limit must be at least 1");
        }
        return unitOfWork.transactional(() -> {
            wallets.findById(walletId).orElseThrow(() -> new NotFoundException("wallet not found"));
            return ledger.page(walletId, afterSequence, Math.min(limit, MAX_PAGE));
        });
    }

    public Reservation reservation(ReservationId id) {
        return unitOfWork.transactional(() -> reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found")));
    }

    public Charge charge(ChargeId id) {
        return unitOfWork.transactional(() -> charges.findById(id).orElseThrow(() -> new NotFoundException("charge not found")));
    }
}
