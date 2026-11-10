package io.corebilling.payment.domain;

public enum ConnectionStatus {
    /** Waiting for the webhook secret (Direct) or for onboarding to finish (Connect). Cannot take payments. */
    PENDING,
    ACTIVE,
    /** Linked, but Stripe has not enabled card payments on the account. Cannot take payments. */
    RESTRICTED,
    /** Replaced or removed. Kept for history; its payments and refunds stay readable. */
    DISCONNECTED;

    public boolean isLive() {
        return this != DISCONNECTED;
    }
}
