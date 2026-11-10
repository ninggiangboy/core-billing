package dev.ngb.core_billing.money.domain.charge;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeSource;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.sharedkernel.IdGenerator;
import dev.ngb.core_billing.sharedkernel.Money;
import java.time.Instant;
import java.util.Objects;
import dev.ngb.core_billing.money.domain.ledger.LedgerEntry;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.money.domain.wallet.Wallet;

/**
 * Decides and applies a charge against a locked wallet: validates it, moves the wallet and builds the ledger
 * entry in one place, so the rules about balance, holds and the Postpaid limit cannot be bypassed.
 */
public final class ChargeService {

    public static final String INSUFFICIENT_FUNDS = "insufficient_funds";

    /** The charge, and the ledger entry it produced (null when the charge failed). */
    public record Outcome(Charge charge, LedgerEntry entry) {}

    /** What the caller supplies for one charge. {@code reservation} is non-null only when charging from one. */
    public record Request(
            Wallet wallet, Reservation reservation, Money amount, ChargeSource source, String usageRef, String reference,
            String idempotencyKey, String requestHash, String actor, Instant now) {
        public Request {
            Objects.requireNonNull(wallet, "wallet");
            Objects.requireNonNull(amount, "amount");
            Objects.requireNonNull(source, "source");
        }
    }

    private final IdGenerator ids;

    public ChargeService(IdGenerator ids) {
        this.ids = ids;
    }

    /**
     * @throws IllegalArgumentException for a non-positive amount or a currency that is not the wallet's
     * @throws IllegalStateException when the reservation cannot be captured
     */
    public Outcome charge(Request request) {
        Wallet wallet = request.wallet();
        Money amount = request.amount();
        if (!amount.isPositive()) {
            throw new IllegalArgumentException("a charge must be positive");
        }
        if (!amount.currency().equals(wallet.currency())) {
            throw new IllegalArgumentException("currency mismatch: wallet is " + wallet.currency() + ", charge is " + amount.currency());
        }
        ChargeId chargeId = new ChargeId(ids.newId());
        ReservationId reservationId = request.reservation() == null ? null : request.reservation().id();

        if (request.reservation() != null) {
            request.reservation().capture(wallet, amount, request.now());
        } else if (!wallet.canSpend(amount)) {
            Charge failed = Charge.failed(chargeId, wallet, amount, request.source(), null, request.usageRef(), request.reference(),
                    request.idempotencyKey(), request.requestHash(), INSUFFICIENT_FUNDS, request.now());
            return new Outcome(failed, null);
        }

        Charge succeeded = Charge.succeeded(chargeId, wallet, amount, request.source(), reservationId, request.usageRef(), request.reference(),
                request.idempotencyKey(), request.requestHash(), request.now());
        long sequence = wallet.post(amount.negate());
        LedgerEntry entry = LedgerEntry.debit(
                new LedgerEntryId(ids.newId()), wallet.tenantId(), wallet.id(), sequence, amount,
                new LedgerEntry.Source(request.reference() == null ? "charge:" + chargeId : request.reference(), reservationId, chargeId),
                new LedgerEntry.Origin(request.actor(), null, request.idempotencyKey()), request.now());
        return new Outcome(succeeded, entry);
    }
}
