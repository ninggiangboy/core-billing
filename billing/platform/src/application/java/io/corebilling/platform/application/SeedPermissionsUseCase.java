package io.corebilling.platform.application;

import io.corebilling.platform.api.Permission;
import io.corebilling.sharedkernel.UnitOfWork;
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
