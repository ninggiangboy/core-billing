package dev.ngb.billing.platform.application.user.query;

import dev.ngb.core.runtime.UnitOfWork;
import dev.ngb.billing.platform.api.error.NotFoundException;
import java.util.List;
import java.util.UUID;

/** Reads users for the admin GET handlers. */
public class UserQueries {

    private final UnitOfWork unitOfWork;
    private final UserReadRepository users;

    public UserQueries(UnitOfWork unitOfWork, UserReadRepository users) {
        this.unitOfWork = unitOfWork;
        this.users = users;
    }

    public List<UserView> list() {
        return unitOfWork.transactional(users::list);
    }

    public UserView get(UUID id) {
        return unitOfWork.transactional(() -> users.findById(id).orElseThrow(() -> new NotFoundException("user not found")));
    }
}
