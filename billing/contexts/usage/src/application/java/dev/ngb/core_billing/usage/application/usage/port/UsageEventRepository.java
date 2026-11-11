package dev.ngb.core_billing.usage.application.usage.port;

import dev.ngb.core_billing.usage.domain.usage.UsageEvent;
import java.util.Optional;

/** Usage events of the current tenant. There is no update and no delete: usage is append-only. */
public interface UsageEventRepository {

    /**
     * Inserts the event unless its idempotency key is already taken, waiting for a concurrent insert of the same
     * key to finish first.
     *
     * @return true when this call stored the event; false when the key already existed
     */
    boolean addIfAbsent(UsageEvent event);

    Optional<UsageEvent> findByIdempotencyKey(String idempotencyKey);
}
