package dev.ngb.billing.platform.application.role.command;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.actor.Permission;
import dev.ngb.billing.platform.application.role.port.PermissionCatalogRepository;
import java.util.List;

/** Copies the in-code permission catalog into the database. Safe to run on every start. */
public class SeedPermissionsUseCase {

    private final UnitOfWork unitOfWork;
    private final PermissionCatalogRepository catalog;

    public SeedPermissionsUseCase(UnitOfWork unitOfWork, PermissionCatalogRepository catalog) {
        this.unitOfWork = unitOfWork;
        this.catalog = catalog;
    }

    public void execute() {
        unitOfWork.transactional(() -> catalog.sync(List.of(Permission.values())));
    }
}
