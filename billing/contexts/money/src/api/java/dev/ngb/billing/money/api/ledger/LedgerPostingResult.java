package dev.ngb.billing.money.api.ledger;

import dev.ngb.billing.sharedkernel.money.Money;

/** {@code replayed} is true when the idempotency key was already used and the original entry is returned. */
public record LedgerPostingResult(LedgerEntryId entryId, long sequence, Money amount, Money balanceAfter, boolean replayed) {}
