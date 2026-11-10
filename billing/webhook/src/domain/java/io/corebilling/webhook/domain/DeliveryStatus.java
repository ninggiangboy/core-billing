package io.corebilling.webhook.domain;

public enum DeliveryStatus {
    /** Waiting for its next attempt. */
    PENDING,
    /** Claimed by a worker; returns to the queue when the lease runs out. */
    IN_PROGRESS,
    SUCCEEDED,
    /** Out of attempts. The row is kept and can be resent. */
    DEAD
}
