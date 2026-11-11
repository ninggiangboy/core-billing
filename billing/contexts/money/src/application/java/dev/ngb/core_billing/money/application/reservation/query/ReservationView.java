package dev.ngb.core_billing.money.application.reservation.query;

import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationStatus;
import dev.ngb.core_billing.sharedkernel.money.Money;
import dev.ngb.core_billing.sharedkernel.id.WalletId;
import java.time.Instant;

/** A reservation as the read API shows it. */
public record ReservationView(
        ReservationId id,
        WalletId walletId,
        ReservationStatus status,
        Money amount,
        Money captured,
        Instant expiresAt,
        String reference,
        Instant createdAt,
        Instant closedAt) {}
