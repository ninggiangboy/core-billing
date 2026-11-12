package dev.ngb.tenant.identity.application.user.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read port for local accounts; it selects straight into views. */
public interface UserReadRepository {

    List<UserView> list();

    Optional<UserView> byId(UUID id);
}
