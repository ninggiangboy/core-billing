package io.corebilling.platform.application.role.command;

import io.corebilling.platform.api.actor.Permission;
import io.corebilling.sharedkernel.UnitOfWork;
import java.util.List;
import io.corebilling.platform.application.role.port.PermissionCatalogRepository;

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
