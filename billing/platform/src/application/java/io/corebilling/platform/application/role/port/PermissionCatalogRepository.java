package io.corebilling.platform.application.role.port;

import io.corebilling.platform.api.actor.Permission;
import java.util.Collection;

/** Mirrors the in-code permission catalog into the database so role grants can reference it. */
public interface PermissionCatalogRepository {

    void sync(Collection<Permission> catalog);
}
