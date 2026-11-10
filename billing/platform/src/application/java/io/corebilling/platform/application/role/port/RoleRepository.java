package io.corebilling.platform.application.role.port;

import io.corebilling.platform.api.error.ConflictException;
import io.corebilling.platform.domain.role.Role;
import io.corebilling.platform.domain.role.RoleId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface RoleRepository {

    /** @throws ConflictException when the name is already used in the scope */
    void add(Role role);

    void update(Role role);

    Optional<Role> findById(RoleId id);

    Optional<Role> findByName(String name);

    List<Role> findAllById(Collection<RoleId> ids);

    List<Role> list();
}
