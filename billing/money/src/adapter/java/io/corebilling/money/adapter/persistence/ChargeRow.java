package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.charge.ChargeSource;
import io.corebilling.money.api.charge.ChargeStatus;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.domain.charge.Charge;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("charges")
record ChargeRow(
        @Id UUID id,
        UUID tenantId,
        UUID walletId,
        String currency,
        long amount,
        String source,
        String status,
        String failureReason,
        UUID reservationId,
        String usageRef,
        String reference,
        String idempotencyKey,
        String requestHash,
        Instant createdAt) {

    static ChargeRow from(Charge charge) {
        return new ChargeRow(
                charge.id().value(),
                charge.tenantId().value(),
                charge.walletId().value(),
                charge.amount().currency().code(),
                charge.amount().minorUnits(),
                charge.source().name(),
                charge.status().name(),
                charge.failureReason(),
                charge.reservationId() == null ? null : charge.reservationId().value(),
                charge.usageRef(),
                charge.reference(),
                charge.idempotencyKey(),
                charge.requestHash(),
                charge.createdAt());
    }

    Charge toDomain() {
        return Charge.restore(
                new ChargeId(id),
                new TenantId(tenantId),
                new WalletId(walletId),
                Money.of(amount, Currency.of(currency.strip())),
                ChargeSource.valueOf(source),
                ChargeStatus.valueOf(status),
                failureReason,
                reservationId == null ? null : new ReservationId(reservationId),
                usageRef,
                reference,
                idempotencyKey,
                requestHash,
                createdAt);
    }
}
