package io.corebilling.money.api;

public enum EntryType {
    CREDIT,
    DEBIT,
    /** Negates an earlier entry; the only way to correct the ledger. */
    REVERSAL
}
