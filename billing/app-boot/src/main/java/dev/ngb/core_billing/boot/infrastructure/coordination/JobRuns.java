package dev.ngb.core_billing.boot.infrastructure.coordination;

import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * Claims a job's run for one key before the work starts. The insert is the claim: a concurrent instance, or this
 * one on a later tick, gets no row and skips the work, so a counter such as {@code billing.ledger.mismatch} cannot
 * count the same run twice.
 */
public class JobRuns {

    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;

    public JobRuns(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(10);
        this.jdbc = jdbc;
    }

    public boolean claim(String job, String runKey, UUID tenantId) {
        Integer rows = transactions.execute(status -> jdbc.update(
                """
                insert into job_runs (job, run_key, tenant_id, started_at)
                values (?, ?, ?, now())
                on conflict (job, run_key, tenant_id) do nothing
                """,
                job, runKey, tenantId));
        return rows != null && rows == 1;
    }
}
