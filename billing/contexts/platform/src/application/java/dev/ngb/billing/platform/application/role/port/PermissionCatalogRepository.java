package dev.ngb.billing.platform.application.role.port;

import dev.ngb.billing.platform.api.actor.Permission;
import java.util.Collection;

/** Mirrors the in-code permission catalog into the database so role grants can reference it. */
public interface PermissionCatalogRepository {

    void sync(Collection<Permission> catalog);
}
