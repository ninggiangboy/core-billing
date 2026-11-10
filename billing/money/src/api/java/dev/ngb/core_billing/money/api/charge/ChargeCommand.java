package dev.ngb.core_billing.money.api.charge;

import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.util.Objects;
import dev.ngb.core_billing.money.api.reservation.ReservationId;

/**
 * Record the actual cost incurred. The source follows from the fields: a {@code reservationId} charges from
 * that reservation (releasing the unused remainder), a {@code usageRef} charges from usage, neither is a
 * standalone charge. For a reservation the wallet is the reservation's and {@code walletId} may be null.
 */
public record ChargeCommand(
        WalletId walletId, ReservationId reservationId, Money amount, String usageRef, String reference, String idempotencyKey) {

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
