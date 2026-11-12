package dev.ngb.tenant.billing.adapter.client.feign;

import dev.ngb.tenant.shared.error.UpstreamUnavailableException;
import feign.RetryableException;
import feign.Retryer;

/** Waits {@code backoff * attempt} between tries, exactly as the previous client did. */
final class LinearRetryer implements Retryer {

    private final int maxAttempts;
    private final long backoffMillis;
    private int attempt = 1;

    LinearRetryer(int maxAttempts, long backoffMillis) {
        this.maxAttempts = maxAttempts;
        this.backoffMillis = backoffMillis;
    }

    @Override
    public void continueOrPropagate(RetryableException e) {
        if (attempt >= maxAttempts) {
            throw e;
        }
        try {
            Thread.sleep(backoffMillis * attempt);
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt();
            throw new UpstreamUnavailableException("the retry wait was interrupted", interrupted);
        }
        attempt++;
    }

    @Override
    public Retryer clone() {
        return new LinearRetryer(maxAttempts, backoffMillis);
    }
}
