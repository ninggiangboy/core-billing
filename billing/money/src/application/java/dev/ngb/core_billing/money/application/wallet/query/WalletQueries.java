package dev.ngb.core_billing.money.application.wallet.query;

import dev.ngb.core_billing.customer.api.lookup.CustomerLookup;
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
import java.util.UUID;

/** Read side of the money module, within the current tenant. */
public class WalletQueries {

    public static final int MAX_PAGE = 200;

    private final UnitOfWork unitOfWork;
    private final WalletReadRepository wallets;
    private final LedgerReadRepository ledger;
    private final ReservationReadRepository reservations;
    private final ChargeReadRepository charges;
    private final CustomerLookup customers;

    public WalletQueries(
            UnitOfWork unitOfWork, WalletReadRepository wallets, LedgerReadRepository ledger, ReservationReadRepository reservations,
            ChargeReadRepository charges, CustomerLookup customers) {
        this.unitOfWork = unitOfWork;
        this.wallets = wallets;
        this.ledger = ledger;
        this.reservations = reservations;
        this.charges = charges;
        this.customers = customers;
    }

    public WalletView wallet(WalletId id) {
        return wallet(id, null);
    }

    public WalletView wallet(WalletId id, UUID caller) {
        return unitOfWork.transactional(() -> requireVisibleWallet(id, caller));
    }

    public List<WalletView> walletsOf(CustomerId customerId) {
        return walletsOf(customerId, null);
    }

    public List<WalletView> walletsOf(CustomerId customerId, UUID caller) {
        return unitOfWork.transactional(() -> {
            requireVisibleCustomer(customerId, caller);
            return wallets.listByCustomer(customerId);
        });
    }

    /** Entries after {@code afterSequence}, oldest first, at most {@link #MAX_PAGE}. */
    public List<LedgerEntryView> ledger(WalletId walletId, long afterSequence, int limit) {
        return ledger(walletId, afterSequence, limit, null);
    }

    public List<LedgerEntryView> ledger(WalletId walletId, long afterSequence, int limit, UUID caller) {
        if (afterSequence < 0 || limit < 1) {
            throw new IllegalArgumentException("afterSequence must not be negative and limit must be at least 1");
        }
        return unitOfWork.transactional(() -> {
            requireVisibleWallet(walletId, caller);
            return ledger.page(walletId, afterSequence, Math.min(limit, MAX_PAGE));
        });
    }

    public ReservationView reservation(ReservationId id) {
        return reservation(id, null);
    }

    public ReservationView reservation(ReservationId id, UUID caller) {
        return unitOfWork.transactional(() -> {
            ReservationView reservation = reservations.findById(id).orElseThrow(() -> new NotFoundException("reservation not found"));
            requireVisible(reservation.walletId(), caller, "reservation not found");
            return reservation;
        });
    }

    public ChargeView charge(ChargeId id) {
        return charge(id, null);
    }

    public ChargeView charge(ChargeId id, UUID caller) {
        return unitOfWork.transactional(() -> {
            ChargeView charge = charges.findById(id).orElseThrow(() -> new NotFoundException("charge not found"));
            requireVisible(charge.walletId(), caller, "charge not found");
            return charge;
        });
    }

    private WalletView requireVisibleWallet(WalletId id, UUID caller) {
        WalletView wallet = wallets.findById(id).orElseThrow(() -> new NotFoundException("wallet not found"));
        if (caller != null && customers.findFor(wallet.customerId(), caller).isEmpty()) {
            throw new NotFoundException("wallet not found");
        }
        return wallet;
    }

    private void requireVisibleCustomer(CustomerId id, UUID caller) {
        if (caller != null && customers.findFor(id, caller).isEmpty()) {
            throw new NotFoundException("customer not found");
        }
    }

    private void requireVisible(WalletId id, UUID caller, String notFound) {
        if (caller == null) {
            return;
        }
        WalletView wallet = wallets.findById(id).orElseThrow(() -> new NotFoundException(notFound));
        if (customers.findFor(wallet.customerId(), caller).isEmpty()) {
            throw new NotFoundException(notFound);
        }
    }
}
