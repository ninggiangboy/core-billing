package io.corebilling.boot.infrastructure;

import io.corebilling.platform.api.RequestScope;
import io.corebilling.platform.api.TenantContext;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The only place that opens transactions and the only place that tells PostgreSQL which tenant a
 * transaction belongs to. The first statement of every new transaction sets {@code app.tenant_id}
 * transaction-locally (the reserved platform tenant for Platform scope), so row-level security applies
 * to everything that follows and nothing survives the commit on a pooled connection.
 */
public class SpringUnitOfWork implements UnitOfWork {

    private static final Object SCOPE_RESOURCE = SpringUnitOfWork.class;

    private final TransactionTemplate transactions;
    private final JdbcTemplate jdbc;
    private final TenantContext tenantContext;

    public SpringUnitOfWork(PlatformTransactionManager transactionManager, JdbcTemplate jdbc, TenantContext tenantContext) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.jdbc = jdbc;
        this.tenantContext = tenantContext;
    }

    @Override
    public <T> T transactional(Supplier<T> work) {
        RequestScope scope = tenantContext.current();
        return transactions.execute(status -> {
            Object joined = TransactionSynchronizationManager.getResource(SCOPE_RESOURCE);
            if (joined != null) {
                if (!joined.equals(scope)) {
                    throw new IllegalStateException("a nested unit of work must run in the same scope as its parent");
                }
                return work.get();
            }
            TransactionSynchronizationManager.bindResource(SCOPE_RESOURCE, scope);
            try {
                jdbc.queryForObject(
                        "select set_config('app.tenant_id', ?, true)", String.class, scope.dataTenantId().toString());
                return work.get();
            } finally {
                TransactionSynchronizationManager.unbindResource(SCOPE_RESOURCE);
            }
        });
    }
}
