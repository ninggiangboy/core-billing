package dev.ngb.core_billing.money.application.wallet.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.core.util.PageSize;
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
import dev.ngb.core_billing.sharedkernel.id.CustomerId;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.util.List;
import java.util.UUID;

/** Read side of the money module, within the current tenant. */
public class WalletQueries {


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

    /** Entries after {@code afterSequence}, oldest first, at most {@link PageSize#MAX}. */
    public List<LedgerEntryView> ledger(WalletId walletId, long afterSequence, int limit) {
        return ledger(walletId, afterSequence, limit, null);
    }

    /** Charges of the given wallets, newest first; the caller has already established that they belong to the customer. */
    public List<ChargeView> charges(List<WalletId> walletIds, UUID before, int limit) {
        if (walletIds.isEmpty()) {
            return List.of();
        }
        return unitOfWork.transactional(() -> charges.list(walletIds.stream().map(WalletId::value).toList(), before, PageSize.of(limit)));
    }

    public List<LedgerEntryView> ledger(WalletId walletId, long afterSequence, int limit, UUID caller) {
        if (afterSequence < 0) {
            throw new IllegalArgumentException("afterSequence must not be negative");
        }
        return unitOfWork.transactional(() -> {
            requireVisibleWallet(walletId, caller);
            return ledger.page(walletId, afterSequence, PageSize.of(limit));
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
