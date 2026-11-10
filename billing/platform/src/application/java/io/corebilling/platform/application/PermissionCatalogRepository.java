package io.corebilling.platform.application;

import io.corebilling.platform.api.Permission;
import java.util.Collection;

/** Mirrors the in-code permission catalog into the database so role grants can reference it. */
public interface PermissionCatalogRepository {

    void sync(Collection<Permission> catalog);
}
