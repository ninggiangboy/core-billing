package dev.ngb.sampletenant.usage.application.usage.port;

import dev.ngb.sampletenant.usage.domain.usage.UsageRecord;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for locally recorded usage. */
public interface UsageRepository {

    void save(UsageRecord record);

    Optional<UsageRecord> findByOrderIdAndMetric(UUID orderId, String metric);
}
