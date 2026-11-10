package io.corebilling.money.api.ledger;

public enum EntryType {
    CREDIT,
    DEBIT,
    /** Negates an earlier entry; the only way to correct the ledger. */
    REVERSAL
}
