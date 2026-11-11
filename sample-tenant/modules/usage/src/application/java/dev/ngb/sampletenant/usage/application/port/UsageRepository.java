package dev.ngb.sampletenant.usage.application.port;

import dev.ngb.sampletenant.usage.domain.UsageRecord;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for locally recorded usage. */
public interface UsageRepository {

    void save(UsageRecord record);

    Optional<UsageRecord> findByOrderIdAndMetric(UUID orderId, String metric);

    List<UsageRecord> findByBillingCustomerId(String billingCustomerId);
}
