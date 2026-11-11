package dev.ngb.sampletenant.resource.adapter.persistence;

import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface OrderJdbcRepository extends CrudRepository<OrderRow, UUID> {

    List<OrderRow> findByCustomerIdOrderByCreatedAtDesc(UUID customerId);

    List<OrderRow> findAllByOrderByCreatedAtDesc();
}
