package dev.ngb.core_billing.platform.application.role.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of roles. */
public interface RoleReadRepository {

    List<RoleView> list();

    Optional<RoleView> findById(UUID id);
}
