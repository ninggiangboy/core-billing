package dev.ngb.core_billing.money.api.wallet;

public enum WalletMode {
    /** The customer tops up first; spending is limited to the available balance. */
    PREPAID,
    /** The customer spends first and pays later, up to a credit limit. */
    POSTPAID
}
