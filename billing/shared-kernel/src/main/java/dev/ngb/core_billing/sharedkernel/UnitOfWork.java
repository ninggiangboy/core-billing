package dev.ngb.core_billing.sharedkernel;

import java.util.function.Supplier;

/**
 * The transaction boundary, owned by the application layer. A use case wraps its whole decision in
 * {@link #transactional(Supplier)}; the implementation joins an existing transaction when one is
 * active and scopes the tenant for row-level security.
 */
public interface UnitOfWork {

    <T> T transactional(Supplier<T> work);

    default void transactional(Runnable work) {
        transactional(() -> {
            work.run();
            return null;
        });
    }
}
