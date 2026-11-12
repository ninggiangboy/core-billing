package dev.ngb.billing.platform.application.user.query;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Read-only projections of Billing users. */
public interface UserReadRepository {

    List<UserView> list();

    Optional<UserView> findById(UUID id);
}
