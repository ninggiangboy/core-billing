package dev.ngb.core_billing.money.application.charge.query;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.charge.ChargeStatus;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.sharedkernel.Money;

/** A charge as the read API shows it. */
public record ChargeView(
        ChargeId id,
        ChargeStatus status,
        String failureReason,
        Money amount,
        ReservationId reservationId,
        String usageRef,
        String reference) {}
