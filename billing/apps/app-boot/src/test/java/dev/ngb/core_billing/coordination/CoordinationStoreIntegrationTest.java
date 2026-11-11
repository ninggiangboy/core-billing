package dev.ngb.core_billing.coordination;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.core_billing.Application;
import dev.ngb.core_billing.boot.infrastructure.coordination.JobLeases;
import dev.ngb.core_billing.boot.infrastructure.coordination.JobRuns;
import dev.ngb.core_billing.boot.infrastructure.coordination.PostgresRateLimiter;
import dev.ngb.core_billing.support.PostgresContainerConfig;
import java.time.Duration;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class CoordinationStoreIntegrationTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void aLeaseAdmitsOneOwnerUntilItExpiresOrIsReleased() {
        String name = "lease-" + UUID.randomUUID();
        JobLeases first = leases();
        JobLeases second = leases();

        assertThat(first.tryAcquire(name, Duration.ofMinutes(1))).isTrue();
        assertThat(second.tryAcquire(name, Duration.ofMinutes(1))).as("a second instance is refused").isFalse();
        assertThat(first.renew(name, Duration.ofMinutes(1))).as("the owner extends its lease").isTrue();
        assertThat(second.renew(name, Duration.ofMinutes(1))).as("a stranger cannot extend it").isFalse();

        first.release(name);
        assertThat(second.tryAcquire(name, Duration.ofMinutes(1))).as("release hands it over").isTrue();

        second.release(name);
        first.tryAcquire(name, Duration.ZERO);
        assertThat(second.tryAcquire(name, Duration.ofMinutes(1))).as("an expired lease can be taken").isTrue();
    }

    @Test
    void aRunIsClaimedOncePerKeyAndTenant() {
        JobRuns runs = new JobRuns(transactions, jdbc);
        String job = "run-" + UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(30);

        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).isTrue();
        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).as("a repeated claim does nothing").isFalse();
        assertThat(runs.claim(job, "2026-11-11", UUID.randomUUID(), ttl)).as("another tenant has its own run").isTrue();
        assertThat(runs.claim(job, "2026-11-12", tenant, ttl)).as("the next key runs again").isTrue();

        runs.release(job, "2026-11-11", tenant);
        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).as("a released claim can be taken again").isTrue();
    }

    @Test
    void anUnfinishedClaimIsTakenOverOnlyAfterItsTtlAndAFinishedOneNever() {
        JobRuns runs = new JobRuns(transactions, jdbc);
        String job = "run-" + UUID.randomUUID();
        UUID tenant = UUID.randomUUID();
        Duration ttl = Duration.ofMinutes(30);

        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).isTrue();
        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).as("a fresh unfinished claim is still owned").isFalse();

        jdbc.update("update job_runs set started_at = now() - interval '31 minutes' where job = ? and run_key = ? and tenant_id = ?",
                job, "2026-11-11", tenant);
        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).as("a claim left by a dead process is taken over").isTrue();

        runs.complete(job, "2026-11-11", tenant);
        jdbc.update("update job_runs set started_at = now() - interval '31 minutes' where job = ? and run_key = ? and tenant_id = ?",
                job, "2026-11-11", tenant);
        assertThat(runs.claim(job, "2026-11-11", tenant, ttl)).as("a finished run is never repeated").isFalse();
    }

    @Test
    void aBucketIsSharedAcrossInstancesAndRefills() {
        String key = "key-" + UUID.randomUUID();
        PostgresRateLimiter first = limiter(3, 0);
        PostgresRateLimiter second = limiter(3, 0);

        assertThat(first.tryAcquire(key)).isTrue();
        assertThat(first.tryAcquire(key)).isTrue();
        assertThat(second.tryAcquire(key)).as("both instances spend from the same bucket").isTrue();
        assertThat(first.tryAcquire(key)).isFalse();
        assertThat(first.tryAcquire("other-" + key)).as("a different key has its own bucket").isTrue();

        jdbc.update("update rate_limit_buckets set updated_at = updated_at - interval '1 second' where bucket_key = ?", key);
        PostgresRateLimiter refilling = limiter(3, 1);
        assertThat(refilling.tryAcquire(key)).as("refill restores one token per second").isTrue();
        assertThat(refilling.tryAcquire(key)).isFalse();
    }

    @Test
    void aBucketExpiresOnlyOnceRefillingItReachesTheFullBurst() {
        String key = "ttl-" + UUID.randomUUID();

        limiter(100, 20).tryAcquire(key);

        Double seconds = jdbc.queryForObject(
                "select extract(epoch from expires_at - now()) from rate_limit_buckets where bucket_key = ?", Double.class, key);
        assertThat(seconds).as("burst 100 at 20 a second is full again in 5 seconds").isBetween(30.0, 90.0);
    }

    @Test
    void cleanupRemovesOnlyExpiredBuckets() {
        String expired = "expired-" + UUID.randomUUID();
        String live = "live-" + UUID.randomUUID();
        jdbc.update(
                "insert into rate_limit_buckets (bucket_key, tokens, updated_at, expires_at) values (?, 0, now(), now() - interval '1 minute')",
                expired);
        jdbc.update(
                "insert into rate_limit_buckets (bucket_key, tokens, updated_at, expires_at) values (?, 0, now(), now() + interval '1 minute')",
                live);

        new PostgresRateLimiter(transactions, jdbc, 1, 0).removeExpired(1_000);

        assertThat(jdbc.queryForObject("select count(*) from rate_limit_buckets where bucket_key = ?", Integer.class, expired))
                .isZero();
        assertThat(jdbc.queryForObject("select count(*) from rate_limit_buckets where bucket_key = ?", Integer.class, live))
                .isOne();
    }

    private JobLeases leases() {
        return new JobLeases(transactions, jdbc);
    }

    private PostgresRateLimiter limiter(int burst, double refillPerSecond) {
        return new PostgresRateLimiter(transactions, jdbc, burst, refillPerSecond);
    }
}
