package dev.ngb.core.runtime;

/**
 * A seam for an application that must run something at the start of every new transaction (for example, telling
 * the database which tenant the transaction belongs to). {@link UnitOfWork} calls it once per outer transaction,
 * after binding {@code scope} and before the work runs.
 */
public interface TransactionScopeHook {

    /** The scope a new transaction will be bound to; the caller compares it with the scope already bound. */
    Object currentScope();

    /** Runs as the first step inside each new transaction. */
    void onNewTransaction(Object scope);

    /** A hook for an application that has no per-transaction work to do. */
    static TransactionScopeHook none() {
        return new TransactionScopeHook() {
            private final Object scope = new Object();

            @Override
            public Object currentScope() {
                return scope;
            }

            @Override
            public void onNewTransaction(Object scope) {}
        };
    }
}
