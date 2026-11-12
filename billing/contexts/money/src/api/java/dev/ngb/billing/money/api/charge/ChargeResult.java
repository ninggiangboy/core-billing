package dev.ngb.billing.money.api.charge;

import dev.ngb.billing.sharedkernel.money.Money;
import dev.ngb.billing.money.api.ledger.LedgerEntryId;

/** A failed charge (for example {@code insufficient_funds}) is a recorded outcome, not an exception. */
public record ChargeResult(
        ChargeId id, ChargeStatus status, String failureReason, Money amount, LedgerEntryId ledgerEntryId, boolean replayed) {}
