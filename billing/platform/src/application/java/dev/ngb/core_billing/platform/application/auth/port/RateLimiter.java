package dev.ngb.core_billing.platform.application.auth.port;

/** Admits or rejects one request for a key. Every instance shares the same store. */
public interface RateLimiter {

    boolean tryAcquire(String key);
}
