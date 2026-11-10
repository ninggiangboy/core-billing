package io.corebilling.money.adapter.persistence;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.ledger.EntryType;
import io.corebilling.money.api.ledger.LedgerEntryId;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.money.domain.ledger.LedgerEntry;
import io.corebilling.sharedkernel.Currency;
import io.corebilling.sharedkernel.Money;
import io.corebilling.sharedkernel.TenantId;
import io.corebilling.sharedkernel.WalletId;
import java.time.Instant;
import java.util.UUID;
import org.springframework.data.annotation.Id;
import org.springframework.data.relational.core.mapping.Table;

@Table("ledger_entries")
record LedgerEntryRow(
        @Id UUID id,
        UUID tenantId,
        UUID walletId,
        String currency,
        long seq,
        String type,
        long amount,
        String reference,
        UUID reservationId,
        UUID chargeId,
        UUID reversalOf,
        String actor,
        String reason,
        String idempotencyKey,
        Instant createdAt) {

    static LedgerEntryRow from(LedgerEntry entry) {
        return new LedgerEntryRow(
                entry.id().value(),
                entry.tenantId().value(),
                entry.walletId().value(),
                entry.amount().currency().code(),
                entry.sequence(),
                entry.type().name(),
                entry.amount().minorUnits(),
                entry.source().reference(),
                entry.source().reservationId() == null ? null : entry.source().reservationId().value(),
                entry.source().chargeId() == null ? null : entry.source().chargeId().value(),
                entry.reversalOf() == null ? null : entry.reversalOf().value(),
                entry.origin().actor(),
                entry.origin().reason(),
                entry.origin().idempotencyKey(),
                entry.createdAt());
    }

    LedgerEntry toDomain() {
        return LedgerEntry.restore(
                new LedgerEntryId(id),
                new TenantId(tenantId),
                new WalletId(walletId),
                seq,
                EntryType.valueOf(type),
                Money.of(amount, Currency.of(currency.strip())),
                new LedgerEntry.Source(reference, reservationId == null ? null : new ReservationId(reservationId),
                        chargeId == null ? null : new ChargeId(chargeId)),
                reversalOf == null ? null : new LedgerEntryId(reversalOf),
                new LedgerEntry.Origin(actor, reason, idempotencyKey),
                createdAt);
    }
}
