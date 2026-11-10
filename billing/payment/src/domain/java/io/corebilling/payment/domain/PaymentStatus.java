package io.corebilling.payment.domain;

/**
 * Where a payment is. The order is the rule: a payment only moves to a later rank, and the three final states
 * never change, so a late or repeated event can never undo what a verified one decided.
 */
public enum PaymentStatus {
    REQUIRES_ACTION(0),
    PROCESSING(1),
    SUCCEEDED(2),
    FAILED(2),
    CANCELED(2);

    private final int rank;

    PaymentStatus(int rank) {
        this.rank = rank;
    }

    public boolean isFinal() {
        return rank == 2;
    }

    public boolean canMoveTo(PaymentStatus next) {
        return !isFinal() && next.rank > rank;
    }
}
