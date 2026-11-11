package dev.ngb.core_billing.boot.infrastructure.coordination;

import java.time.Duration;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * A singleton job's claim on a name, owned until a deadline rather than by a held row lock.
 *
 * <p>Every method is one statement in its own short transaction. The job runs outside those transactions, so a
 * slow run never waits on, or makes anyone wait for, a lease row. Losing the deadline only allows another
 * instance to take over; it never rolls back work in progress.
 */
public class JobLeases {

    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;
    private final String owner = UUID.randomUUID().toString();

    public JobLeases(PlatformTransactionManager transactionManager, JdbcTemplate jdbc) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        this.transactions.setTimeout(10);
        this.jdbc = jdbc;
    }

    /** Takes the lease when it is free or expired, or extends it when this instance already owns it. */
    public boolean tryAcquire(String name, Duration ttl) {
        Integer rows = transactions.execute(status -> jdbc.update(
                """
                insert into job_leases (name, owner, acquired_at, expires_at)
                values (?, ?, now(), now() + make_interval(secs => ?))
                on conflict (name) do update
                    set owner = excluded.owner,
                        acquired_at = excluded.acquired_at,
                        expires_at = excluded.expires_at
                    where job_leases.expires_at <= now()
                       or job_leases.owner = excluded.owner
                """,
                name, owner, Math.toIntExact(ttl.toSeconds())));
        return rows != null && rows > 0;
    }

    /** Extends this instance's lease; false means it was lost to another owner. */
    public boolean renew(String name, Duration ttl) {
        Integer rows = transactions.execute(status -> jdbc.update(
                """
                update job_leases
                set expires_at = now() + make_interval(secs => ?)
                where name = ?
                  and owner = ?
                """,
                Math.toIntExact(ttl.toSeconds()), name, owner));
        return rows != null && rows > 0;
    }

    /** Releases the lease when this instance still owns it. */
    public void release(String name) {
        transactions.execute(status -> jdbc.update(
                "delete from job_leases where name = ? and owner = ?", name, owner));
    }

    public String owner() {
        return owner;
    }
}
