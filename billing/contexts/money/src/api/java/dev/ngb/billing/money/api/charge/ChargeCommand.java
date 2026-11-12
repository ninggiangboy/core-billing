package dev.ngb.billing.money.api.charge;

import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;
import java.util.Objects;
import java.util.UUID;
import dev.ngb.billing.money.api.reservation.ReservationId;

/**
 * Record the actual cost incurred. The source follows from the fields: a {@code reservationId} charges from
 * that reservation (releasing the unused remainder), a {@code usageRef} charges from usage, neither is a
 * standalone charge. For a reservation the wallet is the reservation's and {@code walletId} may be null.
 * {@code caller} is the Tenant System acting over HTTP, or null for a trusted in-process caller.
 */
public record ChargeCommand(
        WalletId walletId, ReservationId reservationId, Money amount, String usageRef, String reference, String idempotencyKey, UUID caller) {

    public ChargeCommand(
            WalletId walletId, ReservationId reservationId, Money amount, String usageRef, String reference, String idempotencyKey) {
        this(walletId, reservationId, amount, usageRef, reference, idempotencyKey, null);
    }

    public ChargeCommand {
        Objects.requireNonNull(amount, "amount");
        Objects.requireNonNull(idempotencyKey, "idempotencyKey");
        if (walletId == null && reservationId == null) {
            throw new IllegalArgumentException("a wallet or a reservation is required");
        }
        if (reservationId != null && usageRef != null) {
            throw new IllegalArgumentException("a charge is from a reservation or from usage, not both");
        }
    }

    public ChargeSource source() {
        return reservationId != null ? ChargeSource.RESERVATION : usageRef != null ? ChargeSource.USAGE : ChargeSource.STANDALONE;
    }
}
