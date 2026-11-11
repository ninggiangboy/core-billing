package dev.ngb.core_billing.payment.application.incident.query;

import dev.ngb.core_billing.sharedkernel.PageSize;
import dev.ngb.core_billing.sharedkernel.UnitOfWork;
import java.util.List;

/** Reads incidents for the GET handler. */
public class IncidentQueries {


    private final UnitOfWork unitOfWork;
    private final IncidentReadRepository incidents;

    public IncidentQueries(UnitOfWork unitOfWork, IncidentReadRepository incidents) {
        this.unitOfWork = unitOfWork;
        this.incidents = incidents;
    }

    public List<IncidentView> list(boolean openOnly, int limit) {
        return unitOfWork.transactional(() -> incidents.list(openOnly, PageSize.of(limit)));
    }
}
