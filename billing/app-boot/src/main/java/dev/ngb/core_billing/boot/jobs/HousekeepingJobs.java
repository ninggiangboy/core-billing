package dev.ngb.core_billing.boot.jobs;

import dev.ngb.core_billing.boot.infrastructure.coordination.PostgresRateLimiter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Removes rate-limit buckets that have been idle past their TTL. Only a worker does this, in bounded
 * {@code skip locked} batches, so cleanup never waits on a bucket an api instance is spending from.
 */
@Profile("!api & !migrate")
@Component
@ConditionalOnProperty(name = "billing.jobs.enabled", havingValue = "true", matchIfMissing = true)
public class HousekeepingJobs {

    private static final Logger LOG = LoggerFactory.getLogger(HousekeepingJobs.class);

    private final PostgresRateLimiter rateLimiter;
    private final int batchSize;

    HousekeepingJobs(
            PostgresRateLimiter rateLimiter,
            @Value("${billing.rate-limit.cleanup-batch:500}") int batchSize) {
        this.rateLimiter = rateLimiter;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${billing.rate-limit.cleanup-delay:PT5M}")
    public void removeExpiredRateLimitBuckets() {
        int removed = rateLimiter.removeExpired(batchSize);
        if (removed > 0) {
            LOG.debug("removed {} expired rate-limit buckets", removed);
        }
    }
}
