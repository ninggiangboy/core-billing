package io.corebilling.usage.application.port;

import io.corebilling.sharedkernel.CustomerId;
import io.corebilling.usage.api.usage.UsageEventId;
import io.corebilling.usage.api.usage.UsageRecord;
import io.corebilling.usage.api.usage.UsageTotal;
import io.corebilling.usage.domain.usage.UsageEvent;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

    /** Newest first; {@code before} is the id of the last row of the previous page. */
    List<UsageEvent> page(CustomerId customerId, String metric, Instant from, Instant to, UsageEventId before, int limit);

    List<UsageRecord> find(CustomerId customerId, Set<String> metrics, Instant from, Instant to, int limit);

    List<UsageTotal> totals(CustomerId customerId, Set<String> metrics, Instant from, Instant to);
}
