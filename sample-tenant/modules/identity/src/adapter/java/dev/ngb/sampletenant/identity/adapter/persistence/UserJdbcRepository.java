package dev.ngb.sampletenant.identity.adapter.persistence;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface UserJdbcRepository extends CrudRepository<UserRow, UUID> {

    Optional<UserRow> findByEmail(String email);

    boolean existsByEmail(String email);

    List<UserRow> findAllByOrderByCreatedAtAsc();
}
