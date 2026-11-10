package io.corebilling.money.application.ledger.query;

import io.corebilling.money.api.charge.ChargeId;
import io.corebilling.money.api.ledger.EntryType;
import io.corebilling.money.api.ledger.LedgerEntryId;
import io.corebilling.money.api.reservation.ReservationId;
import io.corebilling.sharedkernel.Money;
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
