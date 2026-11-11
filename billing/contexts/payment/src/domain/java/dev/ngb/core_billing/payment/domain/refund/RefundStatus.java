package dev.ngb.core_billing.payment.domain.refund;

public enum RefundStatus {
    /** Asked of Stripe (or about to be); the unspent balance is held and the ledger is untouched. */
    PENDING,
    SUCCEEDED,
    FAILED;

    public boolean isFinal() {
        return this != PENDING;
    }
}
