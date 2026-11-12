package dev.ngb.tenant.customer.domain.wallet;

/** How the customer pays: from a topped-up balance, or later against a credit limit. */
public enum WalletMode {
    PREPAID,
    POSTPAID
}
