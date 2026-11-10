package io.corebilling.money.api.charge;

import io.corebilling.sharedkernel.Money;
import io.corebilling.money.api.ledger.LedgerEntryId;

/** A failed charge (for example {@code insufficient_funds}) is a recorded outcome, not an exception. */
public record ChargeResult(
        ChargeId id, ChargeStatus status, String failureReason, Money amount, LedgerEntryId ledgerEntryId, boolean replayed) {}
