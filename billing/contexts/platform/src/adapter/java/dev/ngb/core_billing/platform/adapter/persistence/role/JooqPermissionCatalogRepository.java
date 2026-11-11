package dev.ngb.core_billing.platform.adapter.persistence.role;

import static dev.ngb.core_billing_jooq.Tables.PERMISSIONS;

import dev.ngb.core_billing.platform.api.actor.Permission;
import dev.ngb.core_billing.platform.application.role.port.PermissionCatalogRepository;
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
