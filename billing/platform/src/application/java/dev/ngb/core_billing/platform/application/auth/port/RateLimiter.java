package dev.ngb.core_billing.platform.application.auth.port;

/** Admits or rejects one request for a key. Limits are approximate across instances. */
public interface RateLimiter {

    boolean tryAcquire(String key);
}
