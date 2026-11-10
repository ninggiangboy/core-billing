package io.corebilling.money.application.reservation.query;

import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.api.reservation.ReservationStatus;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.WalletId;
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
