package dev.ngb.core_billing.money.application.ledger.query;

import dev.ngb.core_billing.money.api.charge.ChargeId;
import dev.ngb.core_billing.money.api.ledger.EntryType;
import dev.ngb.core_billing.money.api.ledger.LedgerEntryId;
import dev.ngb.core_billing.money.api.reservation.ReservationId;
import dev.ngb.core_billing.sharedkernel.money.Money;
import java.time.Instant;

/** One ledger entry as the statement page shows it. */
public record LedgerEntryView(
        LedgerEntryId id,
        long sequence,
        EntryType type,
        Money amount,
        String reference,
        ReservationId reservationId,
        ChargeId chargeId,
        LedgerEntryId reversalOf,
        String actor,
        String reason,
        Instant createdAt) {}
