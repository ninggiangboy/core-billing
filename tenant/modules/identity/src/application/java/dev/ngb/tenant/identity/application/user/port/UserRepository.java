package dev.ngb.tenant.identity.application.user.port;

import dev.ngb.tenant.identity.domain.user.User;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for local accounts. */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    boolean emailExists(String email);

    boolean anyExists();

    void save(User user);
}
