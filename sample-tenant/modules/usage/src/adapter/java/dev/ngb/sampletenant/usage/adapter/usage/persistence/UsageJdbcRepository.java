package dev.ngb.sampletenant.usage.adapter.usage.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface UsageJdbcRepository extends CrudRepository<UsageRow, UUID> {

    Optional<UsageRow> findByOrderIdAndMetric(UUID orderId, String metric);

    List<UsageRow> findByBillingCustomerIdOrderByOccurredAtDesc(String billingCustomerId);
}
