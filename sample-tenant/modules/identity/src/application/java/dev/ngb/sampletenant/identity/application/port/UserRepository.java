package dev.ngb.sampletenant.identity.application.port;

import dev.ngb.sampletenant.identity.domain.User;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Persistence boundary for local accounts. */
public interface UserRepository {

    Optional<User> findById(UUID id);

    Optional<User> findByEmail(String email);

    List<User> findAll();

    boolean emailExists(String email);

    void save(User user);
}
