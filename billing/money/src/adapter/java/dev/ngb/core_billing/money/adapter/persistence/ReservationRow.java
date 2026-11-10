package dev.ngb.core_billing.money.adapter.persistence;

import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.money.api.reservation.ReservationStatus;
import dev.ngb.core_billing.money.domain.reservation.Reservation;
import dev.ngb.core_billing.sharedkernel.Currency;
import dev.ngb.core_billing.sharedkernel.Money;
import dev.ngb.core_billing.sharedkernel.TenantId;
import dev.ngb.core_billing.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("reservations")
record ReservationRow(
        @Id UUID id,
        UUID tenantId,
        UUID walletId,
        String currency,
        long amount,
        long capturedAmount,
        String status,
        Instant expiresAt,
        Instant createdAt,
        Instant closedAt,
        String reference,
        String idempotencyKey,
        String requestHash) {

    static ReservationRow from(Reservation reservation) {
        return new ReservationRow(
                reservation.id().value(),
                reservation.tenantId().value(),
                reservation.walletId().value(),
                reservation.amount().currency().code(),
                reservation.amount().minorUnits(),
                reservation.captured().minorUnits(),
                reservation.status().name(),
                reservation.expiresAt(),
                reservation.createdAt(),
                reservation.closedAt(),
                reservation.reference(),
                reservation.idempotencyKey(),
                reservation.requestHash());
    }

    Reservation toDomain() {
        Currency money = Currency.of(currency.strip());
        return Reservation.restore(
                new ReservationId(id),
                new TenantId(tenantId),
                new WalletId(walletId),
                Money.of(amount, money),
                Money.of(capturedAmount, money),
                ReservationStatus.valueOf(status),
                expiresAt,
                createdAt,
                closedAt,
                reference,
                idempotencyKey,
                requestHash);
    }
}
