package dev.ngb.billing.platform.adapter.role.persistence;

import static dev.ngb.billing_jooq.Tables.PERMISSIONS;

import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.application.role.port.PermissionCatalogRepository;
import java.util.Collection;
import org.jooq.DSLContext;

public class JooqPermissionCatalogRepository implements PermissionCatalogRepository {

    private final DSLContext dsl;

    public JooqPermissionCatalogRepository(DSLContext dsl) {
        this.dsl = dsl;
    }

    @Override
    public void sync(Collection<Permission> catalog) {
        for (Permission permission : catalog) {
            dsl.insertInto(PERMISSIONS)
                    .set(PERMISSIONS.CODE, permission.code())
                    .onConflict(PERMISSIONS.CODE)
                    .doNothing()
                    .execute();
        }
    }
}
