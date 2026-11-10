package io.corebilling.platform.adapter.security;

import io.corebilling.platform.application.RateLimiter;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.LongSupplier;

/**
 * A token bucket per key, held in memory. The limit is per instance, so across several instances the
 * effective limit is approximate; the port exists so a shared store can replace this class. The number
 * of tracked keys is bounded, so unknown keys cannot grow memory without limit.
 */
public class InMemoryRateLimiter implements RateLimiter {

    private record Bucket(double tokens, long lastNanos) {}

    private final double burst;
    private final double refillPerNano;
    private final LongSupplier nanoClock;
    private final Map<String, Bucket> buckets;

    public InMemoryRateLimiter(int burst, double refillPerSecond, int maxKeys, LongSupplier nanoClock) {
        this.burst = burst;
        this.refillPerNano = refillPerSecond / 1_000_000_000d;
        this.nanoClock = nanoClock;
        this.buckets = new LinkedHashMap<>(16, 0.75f, true) {
            private static final long serialVersionUID = 1L;

            @Override
            protected boolean removeEldestEntry(Map.Entry<String, Bucket> eldest) {
                return size() > maxKeys;
            }
        };
    }

    @Override
    public synchronized boolean tryAcquire(String key) {
        long now = nanoClock.getAsLong();
        Bucket bucket = buckets.get(key);
        double tokens = bucket == null ? burst : Math.min(burst, bucket.tokens() + (now - bucket.lastNanos()) * refillPerNano);
        if (tokens < 1) {
            buckets.put(key, new Bucket(tokens, now));
            return false;
        }
        buckets.put(key, new Bucket(tokens - 1, now));
        return true;
    }
}
