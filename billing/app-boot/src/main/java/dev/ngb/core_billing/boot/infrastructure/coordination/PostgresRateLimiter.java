package dev.ngb.core_billing.boot.infrastructure.coordination;

import dev.ngb.core_billing.platform.application.auth.port.RateLimiter;
import java.time.Duration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A token bucket per API key id, shared by every api instance through PostgreSQL.
 *
 * <p>The decision happens in one function call that locks one bucket row, and the call runs in its own short
 * transaction. The limiter therefore never extends a business transaction and never adds a lock edge in front of
 * the wallet. A row that has been idle past its TTL is removed by a worker, in bounded {@code skip locked} batches.
 */
public class PostgresRateLimiter implements RateLimiter {

    private static final Duration BUCKET_TTL = Duration.ofDays(1);

    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;
    private final double burst;
    private final double refillPerSecond;

    public PostgresRateLimiter(
            PlatformTransactionManager transactionManager, JdbcTemplate jdbc, int burst, double refillPerSecond) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(10);
        this.jdbc = jdbc;
        this.burst = burst;
        this.refillPerSecond = refillPerSecond;
    }

    @Override
    public boolean tryAcquire(String key) {
        Boolean admitted = transactions.execute(status -> jdbc.queryForObject(
                "select try_acquire_rate_limit(?, ?, ?, ?)",
                Boolean.class,
                key,
                burst,
                refillPerSecond,
                Math.toIntExact(BUCKET_TTL.toSeconds())));
        return Boolean.TRUE.equals(admitted);
    }

    /** Removes buckets that have been idle past their TTL, without waiting on a bucket in use. */
    public int removeExpired(int limit) {
        Integer rows = transactions.execute(status -> jdbc.update(
                """
                delete from rate_limit_buckets
                where bucket_key in (
                    select bucket_key
                    from rate_limit_buckets
                    where expires_at < now()
                    order by expires_at
                    limit ?
                    for update skip locked
                )
                """,
                limit));
        return rows == null ? 0 : rows;
    }
}
