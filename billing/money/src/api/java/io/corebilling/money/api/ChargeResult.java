package io.corebilling.money.api;

import io.corebilling.sharedkernel.Money;

/** A failed charge (for example {@code insufficient_funds}) is a recorded outcome, not an exception. */
public record ChargeResult(
        ChargeId id, ChargeStatus status, String failureReason, Money amount, LedgerEntryId ledgerEntryId, boolean replayed) {}
