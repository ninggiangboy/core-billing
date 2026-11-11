package dev.ngb.core_billing.platform.application.role.query;

import dev.ngb.core_billing.platform.api.error.NotFoundException;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.List;
import java.util.UUID;

/** Reads roles for the admin GET handlers. */
public class RoleQueries {

    private final UnitOfWork unitOfWork;
    private final RoleReadRepository roles;

    public RoleQueries(UnitOfWork unitOfWork, RoleReadRepository roles) {
        this.unitOfWork = unitOfWork;
        this.roles = roles;
    }

    public List<RoleView> list() {
        return unitOfWork.transactional(roles::list);
    }

    public RoleView get(UUID id) {
        return unitOfWork.transactional(() -> roles.findById(id).orElseThrow(() -> new NotFoundException("role not found")));
    }
}
