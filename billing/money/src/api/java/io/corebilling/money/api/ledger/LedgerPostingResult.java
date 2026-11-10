package io.corebilling.money.api.ledger;

import io.corebilling.sharedkernel.Money;

/** {@code replayed} is true when the idempotency key was already used and the original entry is returned. */
public record LedgerPostingResult(LedgerEntryId entryId, long sequence, Money amount, Money balanceAfter, boolean replayed) {}
