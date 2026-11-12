package dev.ngb.sampletenant.customer.adapter.customer.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface CustomerJdbcRepository extends CrudRepository<CustomerRow, UUID> {

    Optional<CustomerRow> findByUserId(UUID userId);

    List<CustomerRow> findAllByOrderByCreatedAtAsc();
}
