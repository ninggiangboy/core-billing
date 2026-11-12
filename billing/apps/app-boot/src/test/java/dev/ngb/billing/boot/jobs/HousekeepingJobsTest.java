package dev.ngb.billing.boot.jobs;

import static org.assertj.core.api.Assertions.assertThat;

import dev.ngb.billing.Application;
import dev.ngb.billing.boot.infrastructure.security.PostgresRateLimiter;
import dev.ngb.billing.support.PostgresContainerConfig;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest(classes = Application.class)
@Import(PostgresContainerConfig.class)
class HousekeepingJobsTest {

    @Autowired JdbcTemplate jdbc;
    @Autowired PlatformTransactionManager transactions;

    @Test
    void aTickDrainsMoreThanOneBatch() {
        String prefix = "cleanup-" + UUID.randomUUID();
        for (int i = 0; i < 3; i++) {
            jdbc.update(
                    "insert into rate_limit_buckets (bucket_key, tokens, updated_at, expires_at) values (?, 0, now(), now() - interval '1 minute')",
                    prefix + "-" + i);
        }

        new HousekeepingJobs(new PostgresRateLimiter(transactions, jdbc, 1, 0), 1).removeExpiredRateLimitBuckets();

        assertThat(jdbc.queryForObject("select count(*) from rate_limit_buckets where bucket_key like ?", Integer.class, prefix + "-%"))
                .as("a batch smaller than the limit ends the tick, so all three are gone")
                .isZero();
    }
}
