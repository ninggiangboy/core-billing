package dev.ngb.sampletenant.identity.adapter.persistence;

import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.CrudRepository;

public interface SessionJdbcRepository extends CrudRepository<SessionRow, UUID> {

    Optional<SessionRow> findByTokenHash(String tokenHash);

    void deleteByTokenHash(String tokenHash);
}
