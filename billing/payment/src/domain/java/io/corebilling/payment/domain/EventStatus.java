package io.corebilling.payment.domain;

public enum EventStatus {
    /** Stored, not yet processed. */
    RECEIVED,
    PROCESSED,
    /** Verified and stored, but nothing to do: a type Billing does not act on, or a state already reached. */
    IGNORED,
    /** Processing failed or had to wait; it will be retried until the attempt limit. */
    FAILED,
    /** Gave up: the attempt limit was reached or the event contradicts what Billing holds. Needs a person. */
    DEAD;

    public boolean isFinal() {
        return this == PROCESSED || this == IGNORED || this == DEAD;
    }
}
