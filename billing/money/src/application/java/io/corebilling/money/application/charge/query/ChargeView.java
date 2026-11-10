package io.corebilling.money.application.charge.query;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.charge.ChargeStatus;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.sharedkernel.Money;

/** A charge as the read API shows it. */
public record ChargeView(
        ChargeId id,
        ChargeStatus status,
        String failureReason,
        Money amount,
        ReservationId reservationId,
        String usageRef,
        String reference) {}
