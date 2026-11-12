package dev.ngb.core.spring.runtime;

import dev.ngb.core.runtime.TransactionScopeHook;
import dev.ngb.core.runtime.UnitOfWork;
import java.util.function.Supplier;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * The only place that opens transactions. The {@link TransactionScopeHook} decides which scope a new transaction
 * runs in and gets the first word inside it, so an application can tell the database which tenant the transaction
 * belongs to before anything else runs. A nested unit of work joins its parent's transaction and must run in the
 * same scope, so a tenant's work can never leak into another tenant's transaction.
 */
public class SpringUnitOfWork implements UnitOfWork {

    private static final Object SCOPE_RESOURCE = SpringUnitOfWork.class;

    private final TransactionTemplate transactions;
    private final TransactionScopeHook hook;

    public SpringUnitOfWork(PlatformTransactionManager transactionManager, TransactionScopeHook hook) {
        this.transactions = new TransactionTemplate(transactionManager);
        this.transactions.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRED);
        this.hook = hook;
    }

    @Override
    public <T> T transactional(Supplier<T> work) {
        Object scope = hook.currentScope();
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
                hook.onNewTransaction(scope);
                return work.get();
            } finally {
                TransactionSynchronizationManager.unbindResource(SCOPE_RESOURCE);
            }
        });
    }
}
