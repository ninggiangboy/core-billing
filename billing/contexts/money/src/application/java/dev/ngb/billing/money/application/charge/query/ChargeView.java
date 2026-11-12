package dev.ngb.billing.money.application.charge.query;

import dev.ngb.billing.money.api.charge.ChargeId;
import dev.ngb.billing.money.api.charge.ChargeSource;
import dev.ngb.billing.money.api.charge.ChargeStatus;
import dev.ngb.billing.money.api.reservation.ReservationId;
import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.sharedkernel.id.WalletId;

import java.time.Instant;

/** A charge as the read API shows it. */
public record ChargeView(
        ChargeId id,
        WalletId walletId,
        ChargeStatus status,
        String failureReason,
        Money amount,
        ReservationId reservationId,
        String usageRef,
        String reference,
        ChargeSource source,
        Instant createdAt) {}
