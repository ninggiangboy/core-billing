package io.corebilling.platform.application;

/** Admits or rejects one request for a key. Limits are approximate across instances. */
public interface RateLimiter {

    boolean tryAcquire(String key);
}
